package com.chellavignesh.authserver.session;

import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.authcode.AuthCodeService;
import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.session.dto.CreateAuthSessionDto;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.session.exception.AuthSessionCreationFailedException;
import com.chellavignesh.authserver.session.exception.FailedToUpdateSessionException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthSessionService {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionService.class);

    private final AuthSessionRepository authSessionRepository;
    private final AuthCodeService authCodeService;

    @Autowired
    public AuthSessionService(AuthSessionRepository authSessionRepository, AuthCodeService authCodeService) {
        this.authSessionRepository = authSessionRepository;
        this.authCodeService = authCodeService;
    }

    public AuthSession create(CreateAuthSessionDto dto) throws AuthSessionCreationFailedException {
        return authSessionRepository.create(dto);
    }

    public Optional<AuthSession> getById(Integer id) {
        return authSessionRepository.getById(id);
    }

    public Optional<AuthSession> getBySessionId(UUID sessionId) {
        return authSessionRepository.getBySessionId(sessionId);
    }

    public void setSessionInactive(UUID sessionId) throws FailedToUpdateSessionException {
        authSessionRepository.setSessionInactive(sessionId);
    }

    public AuthSession createSession(Optional<Application> application, OAuth2Authorization authorization) {

        if (application.isEmpty()) {
            log.error("Application not found for client ID: {}", authorization.getRegisteredClientId());
            throw new RuntimeException("Application not found for client ID: " + authorization.getRegisteredClientId());
        }

        Application app = application.get();
        CreateAuthSessionDto sessionDto = new CreateAuthSessionDto();
        sessionDto.setApplicationId(app.getId());
        sessionDto.setSubjectId(authorization.getPrincipalName());
        sessionDto.setScope(String.join(" ", authorization.getAuthorizedScopes()));
        sessionDto.setAuthFlow(app.getAuthFlow());
        sessionDto.setClientFingerprint(getClientFingerprint(authorization));
        sessionDto.setClientId(authorization.getRegisteredClientId());
        sessionDto.setBranding(getBranding(authorization));

        return this.createSession(sessionDto);
    }

    public AuthSession createSession(CreateAuthSessionDto sessionDto) {
        try {
            return this.create(sessionDto);
        } catch (AuthSessionCreationFailedException e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static byte @Nullable [] getClientFingerprint(OAuth2Authorization authorization) {
        // The client fingerprint should have been added to the authorization
        // by CustomOAuth2AuthorizationCodeRequestConverter
        byte[] clientFingerprint = null;

        if (authorization.getAuthorizationGrantType() == AuthorizationGrantType.AUTHORIZATION_CODE) {

            var authorizationRequest = (OAuth2AuthorizationRequest) authorization.getAttribute("org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest");

            if (authorizationRequest == null) {
                log.error("Failed to retrieve client fingerprint from authorization request");
                throw new RuntimeException("Could not retrieve client fingerprint from authorization request");
            }

            clientFingerprint = (byte[]) authorizationRequest.getAdditionalParameters().get("client_fingerprint");
        }

        return clientFingerprint;
    }

    private static @Nullable String getBranding(OAuth2Authorization authorization) {
        // The branding should have been added to the authorization
        // by CustomOAuth2AuthorizationCodeRequestConverter or set in session
        String branding = null;

        if (authorization.getAuthorizationGrantType() == AuthorizationGrantType.AUTHORIZATION_CODE) {

            var authorizationRequest = (OAuth2AuthorizationRequest) authorization.getAttribute("org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest");

            if (authorizationRequest != null) {
                branding = (String) authorizationRequest.getAdditionalParameters().get(ApplicationConstants.BRANDING_INFO);
            }

            // If branding is not in the authorization request, use default
            if (branding == null) {
                log.debug("Branding not found in authorization request, using default");
                branding = ApplicationConstants.DEFAULT_BRANDING;
            }
        }

        return branding;
    }

    public Optional<AuthSession> findSessionByAuthorization(OAuth2Authorization authorization) {
        return getByAuthorization(authorization);
    }

    public void setBrandingAndRedirectUri(UUID sessionId, String branding, String redirectUri, Integer applicationId) throws FailedToUpdateSessionException {
        authSessionRepository.setBrandingAndRedirectUri(sessionId, branding, redirectUri, applicationId);
    }

    private Optional<AuthSession> getByAuthorization(OAuth2Authorization authorization) {
        String authCode = authorization.getToken(OAuth2AuthorizationCode.class).getToken().getTokenValue();

        Optional<UUID> sessionId = authCodeService.getSessionIdByAuthCode(authCode);

        if (sessionId.isEmpty()) {
            return Optional.empty();
        }

        return authSessionRepository.getBySessionId(sessionId.get());
    }
}
