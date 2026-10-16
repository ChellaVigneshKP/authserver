package com.chellavignesh.authserver.config;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.application.entity.ApplicationDetail;
import com.chellavignesh.authserver.adminportal.application.exception.AppNotFoundException;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.biometric.BiometricAuthenticationToken;
import com.chellavignesh.authserver.cms.BrandUrlMappingService;
import com.chellavignesh.authserver.controller.MFAController;
import com.chellavignesh.authserver.mfa.MFAService;
import com.chellavignesh.authserver.mfa.dto.OtpReceiverDto;
import com.chellavignesh.authserver.session.AuthSessionService;
import com.chellavignesh.authserver.session.PasswordEncoderManager;
import com.chellavignesh.authserver.session.dto.CreateAuthSessionDto;
import com.chellavignesh.authserver.session.dto.CustomUserDetails;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.session.sso.SingleSignOnService;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.*;

@Slf4j
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final RequestCache requestCache = new HttpSessionRequestCache();
    private final ApplicationService applicationService;
    private final AuthSessionService authSessionService;
    private final SingleSignOnService singleSignOnService;
    private final BrandUrlMappingService brandUrlMappingService;
    private final PasswordEncoderManager passwordEncoderManager;

    private final boolean useUpdatedLogin;

    private MFAService mfaService;

    public CustomAuthenticationSuccessHandler(UserService userService, ApplicationService applicationService, AuthSessionService authSessionService, SingleSignOnService singleSignOnService, BrandUrlMappingService brandUrlMappingService, PasswordEncoderManager passwordEncoderManager, @Value("${toggles.updated-login.enabled}") boolean useUpdatedLogin) {
        this.userService = userService;
        this.applicationService = applicationService;
        this.authSessionService = authSessionService;
        this.singleSignOnService = singleSignOnService;
        this.brandUrlMappingService = brandUrlMappingService;
        this.passwordEncoderManager = passwordEncoderManager;
        this.useUpdatedLogin = useUpdatedLogin;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

        HttpSession session = request.getSession();
        String branding = (String) session.getAttribute(ApplicationConstants.BRANDING_INFO);

        upgradePasswordVersion(request, authentication, branding);

        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest == null) {
            log.error("Couldn't find a saved /authorize request");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Initiate authentication with /authorize request");
            return;
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        session.setAttribute(ApplicationConstants.AUTHENTICATED_BRANDING, branding);

        session.setAttribute(ApplicationConstants.LAST_LOGIN, userDetails.getLastLogin());

        AuthSession authSession = createAuthSession(session, savedRequest, userDetails.getUsername());

        try {
            var ssoCookie = singleSignOnService.generateCookie(authSession.getSessionId());
            response.addCookie(createSsoCookie(session, ssoCookie.encryptedSessionId()));
        } catch (Exception e) {
            handleUnexpectedException(response, authentication, "Failed to create encrypted SSO Cookie", e);
            return;
        }

        log.debug("Checking MFA for user: {}", userDetails.getUsername());

        if (!(authentication instanceof BiometricAuthenticationToken) && userDetails.isTwoFactorEnabled()) {

            if (!useUpdatedLogin) {
                try {
                    Optional<ApplicationDetail> applicationDetail = applicationService.getApplicationDetailById(authSession.getApplicationId());

                    if (applicationDetail.isEmpty()) {
                        throw new AppNotFoundException("Application not found in the AuthSession");
                    }

                    OtpReceiverDto receiver = OtpReceiverDto.forSessionId(authSession.getSessionId());

                    mfaService.generateAndSendOTPCode(receiver, applicationDetail.get().getMfaRealmId());
                } catch (Exception e) {
                    handleUnexpectedException(response, authentication, "Failed to generate MFA OTP code", e);
                    return;
                }
            }

            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new PreAuthGrantedAuthority());
            authorities.addAll(authentication.getAuthorities());

            Authentication preAuth = new UsernamePasswordAuthenticationToken(userDetails, authentication.getCredentials(), authorities);

            SecurityContextHolder.getContext().setAuthentication(preAuth);
            new org.springframework.security.web.context.HttpSessionSecurityContextRepository()
                    .saveContext(SecurityContextHolder.getContext(), request, response);

            String targetUrl;
            if (!useUpdatedLogin) {
                targetUrl = spliceBrandedUrl(session, "/services" + MFAController.MFA_URL);
            } else {
                targetUrl = spliceBrandedUrl(session, "/services" + MFAController.MFA_URL + "/login/request-pin");
            }
            log.info("2FA Enabled. Redirecting to {}", targetUrl);
            response.sendRedirect(targetUrl);
        } else {
            if (authentication instanceof BiometricAuthenticationToken) {
                // The proof has already been verified. Persist the standard Spring token,
                // excluding the one-use provider credential and custom token type.
                Authentication established = UsernamePasswordAuthenticationToken.authenticated(
                        userDetails, null, authentication.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(established);
                new org.springframework.security.web.context.HttpSessionSecurityContextRepository()
                        .saveContext(SecurityContextHolder.getContext(), request, response);
            }
            userService.updateAccessFailedCountWithExternalSourceCode(userDetails.getUsername(), branding, 1);

            String targetUrl = spliceBrandedUrl(session, savedRequest.getRedirectUrl());

            response.sendRedirect(targetUrl);
        }
    }

    private void upgradePasswordVersion(HttpServletRequest request, Authentication authentication, String branding) {
        if (!(authentication instanceof UsernamePasswordAuthenticationToken)) {
            return;
        }

        try {
            String rawPassword = request.getParameter("password");
            String password = rawPassword != null ? new String(Base64.getDecoder().decode(rawPassword.getBytes())) : null;

            passwordEncoderManager.processPassword(authentication, password, branding);
        } catch (Exception e) {
            log.error("Error updating the user password hash and version", e);
        }
    }

    AuthSession createAuthSession(HttpSession session, SavedRequest savedRequest, String userName) throws BadRequestException{

        Map<String, String[]> params = savedRequest.getParameterMap();

        String clientId = getParameter(params, OAuth2ParameterNames.CLIENT_ID);
        String scopes = getParameter(params, OAuth2ParameterNames.SCOPE);
        String branding = (String) session.getAttribute(ApplicationConstants.BRANDING_INFO);

        Optional<Application> application = applicationService.getByClientId(clientId);

        if (application.isEmpty()) {
            throw new BadRequestException("Failed to locate registered application for client ID: " + clientId);
        }

        CreateAuthSessionDto dto = new CreateAuthSessionDto();
        dto.setApplicationId(application.get().getId());
        dto.setSubjectId(userName);
        dto.setScope(scopes);
        dto.setAuthFlow(application.get().getAuthFlow());
        dto.setClientFingerprint(getClientFingerprint(session));
        dto.setClientId(clientId);
        dto.setBranding(branding);

        AuthSession authSession = authSessionService.createSession(dto);

        session.setAttribute(ApplicationConstants.AUTH_SESSION_ID, authSession.getSessionId());

        return authSession;
    }

    @Nullable
    private byte[] getClientFingerprint(HttpSession session) {
        Object fingerprint = session.getAttribute(ApplicationConstants.CLIENT_FINGERPRINT);
        if (fingerprint instanceof byte[] bytes) return bytes.clone();
        // Jackson's session serializer represents a standalone byte array as Base64.
        if (fingerprint instanceof String encoded) {
            try { return Base64.getDecoder().decode(encoded); }
            catch (IllegalArgumentException exception) { return null; }
        }
        return null;
    }

    private String getParameter(Map<String, String[]> params, String name) throws BadRequestException{
        String[] values = params.get(name);
        if (values == null || values.length == 0) {
            throw new BadRequestException("Request is missing required parameter: " + name);
        }
        return values[0];
    }

    private Cookie createSsoCookie(HttpSession session, byte[] encryptedSessionId) {
        Cookie cookie = new Cookie(ApplicationConstants.SSO_COOKIE_NAME, Base64.getEncoder().encodeToString(encryptedSessionId));

        cookie.setHttpOnly(true);
        cookie.setSecure("https".equalsIgnoreCase(brandUrlMappingService.getUrlByBrand((String) session.getAttribute(ApplicationConstants.BRANDING_INFO)).getScheme()));
        cookie.setPath(java.net.URI.create(brandUrlMappingService.getUrlByBrand((String) session.getAttribute(ApplicationConstants.BRANDING_INFO)).toString()).getPath());
        cookie.setAttribute("SameSite", "Strict");

        // Host-only cookie; tenant host is selected by the browser origin.
        return cookie;
    }

    private String spliceBrandedUrl(HttpSession session, String redirectUrl) {
        String branding = (String) session.getAttribute(ApplicationConstants.BRANDING_INFO);

        if (brandUrlMappingService.isDefault(branding)) {
            return redirectUrl;
        }

        URI uri = URI.create(redirectUrl);
        URI brandedUri = brandUrlMappingService.getUrlByBrand(branding);

        return UriComponentsBuilder.fromUri(brandedUri).replacePath(uri.getPath()).query(uri.getQuery()).build().toString();
    }

    private void handleUnexpectedException(HttpServletResponse response, Authentication authentication, String message, Exception exception) throws IOException {

        Authentication unauth = new UsernamePasswordAuthenticationToken(authentication.getPrincipal(), authentication.getCredentials());

        SecurityContextHolder.getContext().setAuthentication(unauth);

        log.error(message, exception);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message);
    }
}

