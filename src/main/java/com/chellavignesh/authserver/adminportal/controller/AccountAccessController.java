package com.chellavignesh.authserver.adminportal.controller;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.application.exception.AppNotFoundException;
import com.chellavignesh.authserver.adminportal.externalsource.ExternalSourceService;
import com.chellavignesh.authserver.adminportal.externalsource.entity.ExternalSource;
import com.chellavignesh.authserver.adminportal.externalsource.exception.InvalidBrandingException;
import com.chellavignesh.authserver.adminportal.forgotcredentials.ForgotCredentialsService;
import com.chellavignesh.authserver.adminportal.forgotusername.exception.InvalidUserSessionException;
import com.chellavignesh.authserver.adminportal.toggle.AccountSyncType;
import com.chellavignesh.authserver.adminportal.user.OnPremAccountServiceClient;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.dto.ResetUserPasswordDto;
import com.chellavignesh.authserver.adminportal.user.dto.validator.UserDtoValidator;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.chellavignesh.authserver.adminportal.user.exception.AccountSyncException;
import com.chellavignesh.authserver.adminportal.user.exception.MobileAccountAccessBadRequestException;
import com.chellavignesh.authserver.adminportal.user.exception.UserNotFoundException;
import com.chellavignesh.authserver.adminportal.user.exception.UserUpdateFailedException;
import com.chellavignesh.authserver.adminportal.util.SkipInitBinder;
import com.chellavignesh.authserver.adminportal.util.UUIDUtils;
import com.chellavignesh.authserver.cms.BrandUrlMappingService;
import com.chellavignesh.authserver.cms.CmsService;
import com.chellavignesh.authserver.cms.exception.CmsBadRequestException;
import com.chellavignesh.authserver.cms.exception.CmsFileNotFoundException;
import com.chellavignesh.authserver.cms.exception.CmsProcessingException;
import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.config.PreAuthGrantedAuthority;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import com.chellavignesh.authserver.enums.entity.UsernameTypeEnum;
import com.chellavignesh.authserver.security.PasswordValidatorService;
import com.chellavignesh.authserver.security.exception.PasswordIncorrectSemanticException;
import com.chellavignesh.authserver.security.exception.PasswordRecentlyUsedException;
import com.chellavignesh.authserver.security.exception.PasswordValidationException;
import com.chellavignesh.authserver.session.AuthSessionService;
import com.chellavignesh.authserver.session.CustomUserDetailsService;
import com.chellavignesh.authserver.session.dto.CreateAuthSessionDto;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.session.exception.AuthSessionNotFoundException;
import com.chellavignesh.authserver.session.exception.InvalidSessionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.*;

@Slf4j
@Controller
@RequestMapping(value = {"/forgot-password", "/activate-account", "/mobile-forgot-password"})
public class AccountAccessController {

    private static final String APPLICATION_LOGIN_ENTRY_POINT_LINK = "/services/login";
    private static final String ACTIVATE_ACCOUNT_FLOW = "activate-account";
    private static final String FORGOT_PASSWORD_FLOW = "forgot-password";
    private static final String MOBILE_FORGOT_PASSWORD_FLOW = "mobile-forgot-password";
    private static final String MOBILE_FORGOT_USERNAME_FLOW = "mobile-forgot-username";
    private static final String FORGOT_USERNAME_BUTTON_TEXT = "Forgot Username";
    private static final String FORGOT_USERNAME_BUTTON_COLOR = "#2d65b4";

    private final AccountSyncType accountSyncType;
    private final ForgotCredentialsService forgotPasswordService;
    private final CmsService cmsService;
    private final UserService userService;
    private final AuthSessionService authSessionService;
    private final ApplicationService applicationService;
    private final UserDtoValidator userDtoValidator;
    private final BrandUrlMappingService brandUrlMappingService;
    private final CustomUserDetailsService customUserDetailsService;
    private final PasswordValidatorService passwordValidatorService;
    private final OnPremAccountServiceClient onPremAccountService;
    private final ExternalSourceService externalSourceService;

    @Value("${toggles.enable.account.search:false}")
    private boolean isAccountSearchEnabled;

    @InitBinder
    public void initBinder(WebDataBinder binder, HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod handlerMethod) {
            SkipInitBinder skipAnnotation = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), SkipInitBinder.class);
            if (skipAnnotation != null) {
                return;
            }
        }
        binder.setValidator(userDtoValidator);
    }

    @Autowired
    public AccountAccessController(ForgotCredentialsService forgotPasswordService, ApplicationService applicationService, CmsService cmsService, UserService userService, AuthSessionService authSessionService, UserDtoValidator userDtoValidator, BrandUrlMappingService brandUrlMappingService, CustomUserDetailsService customUserDetailsService, PasswordValidatorService passwordValidatorService, OnPremAccountServiceClient onPremAccountService, @Value("${toggles.account.sync.type:sync}") AccountSyncType accountSyncType, ExternalSourceService externalSourceService) {
        this.forgotPasswordService = forgotPasswordService;
        this.cmsService = cmsService;
        this.userService = userService;
        this.applicationService = applicationService;
        this.authSessionService = authSessionService;
        this.userDtoValidator = userDtoValidator;
        this.brandUrlMappingService = brandUrlMappingService;
        this.customUserDetailsService = customUserDetailsService;
        this.passwordValidatorService = passwordValidatorService;
        this.onPremAccountService = onPremAccountService;
        this.accountSyncType = accountSyncType;
        this.externalSourceService = externalSourceService;
    }

    @GetMapping
    public String getAccountAccessHomeScreen(@RequestParam(value = "cid", required = false) String clientIdParam, @RequestParam(value = "eid", required = false) String hexExternalIdParam, Model model, HttpServletRequest request) throws AppNotFoundException, CmsBadRequestException, CmsProcessingException, CmsFileNotFoundException, InvalidUserSessionException, MobileAccountAccessBadRequestException {
        final var flow = getFlow(request);
        String clientId;
        String branding;

        var session = request.getSession();
        if (session == null) {
            throw new InvalidUserSessionException("User session invalid or in invalid state");
        }

        if (MOBILE_FORGOT_PASSWORD_FLOW.equals(flow)) {
            validateMobileForgotPasswordParameters(clientIdParam, hexExternalIdParam);
            clientId = clientIdParam;
            branding = getBranding(hexExternalIdParam);
            session.setAttribute(ApplicationConstants.CLIENT_ID, clientId);
            session.setAttribute(ApplicationConstants.BRANDING_INFO, branding);
            session.setAttribute(ApplicationConstants.HEX_EXTERNAL_ID, hexExternalIdParam);
        } else {
            clientId = getClientIdFromRequestSession(request);
            branding = (String) request.getAttribute(ApplicationConstants.BRANDING_INFO);
        }

        var cmsData = cmsService.getCmsInfoForRequest(request);
        Application application = forgotPasswordService.checkClientId(clientId).get();

        this.setIdentifyYourselfPageModelAttributes(model, flow, application, cmsData, request);
        return "pages/account-access/identify-yourself";
    }

    @PostMapping("/identify-yourself")
    public String postAccountAccessIdentifyYourself(Model model, HttpServletRequest request, @RequestParam(value = "username", required = false) String username, @RequestParam(value = "email", required = false) String email, @SessionAttribute(value = ApplicationConstants.BRANDING_INFO, required = false) String brand) throws CmsBadRequestException, CmsProcessingException, CmsFileNotFoundException, AppNotFoundException {

        final var flow = getFlow(request);
        var redirectRootUrl = brandUrlMappingService.getUrlByBrand(brand);
        String clientId = getClientIdFromRequestSession(request);

        Application application = applicationService.getByClientId(clientId).orElseThrow(() -> new AppNotFoundException("Application could not be found during lookup."));

        var cmsData = cmsService.getCmsInfoForRequest(request);
        UserDetails userDetails;

        try {
            if (username != null) {
                userDetails = userService.getByUsernameAndBranding(username, brand).orElseThrow(() -> new UserNotFoundException("Username could not be found during lookup."));

                if (isAccountSearchEnabled && !customUserDetailsService.getUserApprovalStatus(userDetails.branding(), username)) {

                    request.getSession().setAttribute("approvalPending", cmsData.getOrDefault("approvalPending", null));

                    return "redirect:%s/%s".formatted(redirectRootUrl, flow);
                }
            } else {
                userDetails = userService.getByUsernameAndBranding(email, brand).orElseThrow(() -> new UserNotFoundException("Username could not be found during lookup."));
            }
        } catch (Exception e) {
            log.error("Exception thrown looking up user {}: {}", username, e.getMessage());

            request.getSession().setAttribute("noUserFoundError", cmsData.getOrDefault("noUserFoundError", null));

            if (MOBILE_FORGOT_PASSWORD_FLOW.equals(flow)) {
                var hexExternalId = request.getSession().getAttribute(ApplicationConstants.HEX_EXTERNAL_ID);
                return "redirect:%s/%s?cid=%s&eid=%s".formatted(redirectRootUrl, flow, clientId, hexExternalId);
            } else {
                return "redirect:%s/%s".formatted(redirectRootUrl, flow);
            }
        }

        // Create temporary session for MFA
        CreateAuthSessionDto sessionDto = new CreateAuthSessionDto();
        sessionDto.setApplicationId(application.getId());
        sessionDto.setSubjectId(userDetails.username());
        sessionDto.setScope("forgot-password");
        sessionDto.setAuthFlow(AuthFlowEnum.PKCE);
        sessionDto.setClientFingerprint(null);
        sessionDto.setClientId(clientId);
        sessionDto.setBranding(brand);

        AuthSession authSession = authSessionService.createSession(sessionDto);
        request.getSession().setAttribute(ApplicationConstants.AUTH_SESSION_ID, authSession.getSessionId());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, Collections.singleton(new PreAuthGrantedAuthority()));

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        return "redirect:%s/mfa/%s/request-pin".formatted(redirectRootUrl, flow);
    }

    @GetMapping("/update-password")
    public String getAccountAccessUpdatePassword(Model model, HttpServletRequest request) throws CmsBadRequestException, CmsProcessingException, CmsFileNotFoundException, InvalidUserSessionException {

        final var flow = getFlow(request);
        var sessionId = request.getSession();

        if (sessionId == null) {
            throw new InvalidUserSessionException("User session invalid or in invalid state - Forgot Password Update");
        }

        var cmsData = cmsService.getCmsInfoForRequest(request);

        if (request.getSession().getAttribute("error") != null) {
            model.addAttribute("error", request.getSession().getAttribute("error").toString());
            request.getSession().removeAttribute("error");
        }

        // Plan details
        model.addAttribute("planName", cmsData.getOrDefault("planName", ""));
        model.addAttribute("planFaviconPath", cmsData.getOrDefault("planFaviconPath", ""));

        model.addAttribute("flow", flow);
        model.addAttribute("cancelUrl", APPLICATION_LOGIN_ENTRY_POINT_LINK);
        model.addAttribute("contact", cmsData.getOrDefault("helpContentHTML", ""));
        model.addAttribute("logo", cmsData.getOrDefault("loginLogoPath", ""));

        model.addAttribute("progressSidebarLinks", List.of(cmsData.getOrDefault("progressBarLinkHTML1", ""), cmsData.getOrDefault("progressBarLinkHTML2", ""), cmsData.getOrDefault("progressBarLinkHTML3", ""), cmsData.getOrDefault("progressBarLinkHTML4", ""), cmsData.getOrDefault("progressBarLinkHTML5", ""), cmsData.getOrDefault("progressBarLinkHTML6", "")));

        model.addAttribute("footerLinks", cmsData.getOrDefault("footerLinks", ""));
        model.addAttribute("footerHTML", cmsData.getOrDefault("footerHTML", ""));
        model.addAttribute("planPrimaryColor", cmsData.getOrDefault("planPrimaryColor", ""));
        model.addAttribute("planSecondaryColor", cmsData.getOrDefault("planSecondaryColor", ""));
        model.addAttribute("planTertiaryColor", cmsData.getOrDefault("planTertiaryColor", ""));
        model.addAttribute("planPrimaryTextColor", cmsData.getOrDefault("planPrimaryTextColor", ""));
        model.addAttribute("planSecondaryTextColor", cmsData.getOrDefault("planSecondaryTextColor", ""));
        model.addAttribute("planTertiaryTextColor", cmsData.getOrDefault("planTertiaryTextColor", ""));
        model.addAttribute("planPublicWebLink", cmsData.getOrDefault("planPublicWebLink", ""));

        if (ACTIVATE_ACCOUNT_FLOW.equals(flow)) {

            model.addAttribute("description", cmsData.getOrDefault("activateAccountSetPasswordContent2", ""));
            model.addAttribute("metaTitle", "Activate Account - ".concat(cmsData.getOrDefault("activateAccountNavLink4", "")));

            model.addAttribute("progressSidebarSteps", List.of(cmsData.getOrDefault("activateAccountNavLink1", ""), cmsData.getOrDefault("activateAccountNavLink2", ""), cmsData.getOrDefault("activateAccountNavLink3", ""), cmsData.getOrDefault("activateAccountNavLink4", "")));

            model.addAttribute("subtitle", cmsData.getOrDefault("activateAccountSetPasswordContent", ""));
            model.addAttribute("title", cmsData.getOrDefault("activateAccountSetPasswordTitle", ""));

        } else {

            model.addAttribute("description", cmsData.getOrDefault("forgotPasswordSetPasswordContent2", ""));
            model.addAttribute("metaTitle", "Forgot Password - ".concat(cmsData.getOrDefault("forgotPasswordNavLink4", "")));

            model.addAttribute("progressSidebarSteps", List.of(cmsData.getOrDefault("forgotPasswordNavLink1", ""), cmsData.getOrDefault("forgotPasswordNavLink2", ""), cmsData.getOrDefault("forgotPasswordNavLink3", ""), cmsData.getOrDefault("forgotPasswordNavLink4", "")));

            model.addAttribute("subtitle", cmsData.getOrDefault("forgotPasswordSetPasswordContent", ""));
            model.addAttribute("title", cmsData.getOrDefault("forgotPasswordSetPasswordTitle", ""));
            if (MOBILE_FORGOT_PASSWORD_FLOW.equals(flow)) {
                model.addAttribute("disableCancel", "disableCancel");
                model.addAttribute("disableBack", "disableBack");

                model.addAttribute("success", request.getSession().getAttribute("success"));
                request.getSession().removeAttribute("success");
            }
        }

        return "pages/account-access/update-password";
    }

    @PostMapping("/update-password")
    @SkipInitBinder
    public String postAccountAccessUpdatePassword(HttpServletRequest request, @Valid @ModelAttribute ResetUserPasswordDto resetUserPasswordDto, BindingResult bindingResult, @RequestHeader Map<String, String> headers) throws AppNotFoundException, InvalidUserSessionException, UserNotFoundException {

        final var flow = getFlow(request);
        request.getSession().removeAttribute("error");

        String clientId = getClientIdFromRequestSession(request);
        String branding = (String) request.getSession().getAttribute(ApplicationConstants.BRANDING_INFO);
        String sessionId = (String) request.getSession().getAttribute(ApplicationConstants.AUTH_SESSION_ID);

        if (sessionId == null || clientId == null || branding == null) {
            throw new InvalidUserSessionException("User session invalid or in invalid state - Forgot Password Update");
        }

        if (bindingResult.hasErrors()) {
            String message = Objects.requireNonNull(bindingResult.getFieldError()).getDefaultMessage();
            request.getSession().setAttribute("error", message);

            return "redirect:%s/%s/update-password".formatted(brandUrlMappingService.getUrlByBrand(branding), flow);
        }

        try {
            resetUserPasswordDto.setPassword(new String(Base64.getDecoder().decode(resetUserPasswordDto.getPassword())));

            UserDetails user = forgotPasswordService.getUser(UUID.fromString(sessionId), clientId, branding);

            passwordValidatorService.validatePassword(resetUserPasswordDto.getPassword(), user.rowGuid());

            Application application = forgotPasswordService.checkClientId(clientId).get();

            boolean isEntity = application.getCmsContext() != null && application.getCmsContext().equals("entity");

            if ((!isEntity || user.credSyncFlag()) && AccountSyncType.SYNC == accountSyncType) {

                var requestId = request.getHeader(ApplicationConstants.REQUEST_ID_HEADER);

                var response = onPremAccountService.syncPassword(resetUserPasswordDto.getPassword(), null, user, branding, requestId);

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.error("Received a non-200 response from On-prem Account service while trying to sync password: {} body: {}", response.statusCode(), response.body());

                    request.getSession().setAttribute("error", "Failed to sync password");

                    return "redirect:%s/%s/update-password".formatted(brandUrlMappingService.getUrlByBrand(branding), flow);
                }

            } else if (Boolean.TRUE.equals(user.credSyncFlag()) && AccountSyncType.ASYNC == accountSyncType) {

                forgotPasswordService.resetUserPasswordAndNotify(resetUserPasswordDto, user.rowGuid(), branding, headers);

            } else {
                forgotPasswordService.resetUserPassword(resetUserPasswordDto, user.rowGuid(), branding);
            }

            userService.unlockAccount(user.username(), user.branding());

            request.getSession().setAttribute("success", "Password updated successfully");
            request.getSession().removeAttribute("error");
            request.getSession().removeAttribute("noUserFoundError");

            if (MOBILE_FORGOT_PASSWORD_FLOW.equals(flow)) {
                request.getSession().removeAttribute(ApplicationConstants.AUTH_SESSION_ID);

                return "redirect:%s/%s/update-password".formatted(brandUrlMappingService.getUrlByBrand(branding), flow);
            } else {
                request.getSession().removeAttribute(ApplicationConstants.AUTH_SESSION_ID);

                return "redirect:%s/login".formatted(brandUrlMappingService.getUrlByBrand(branding));
            }

        } catch (PasswordRecentlyUsedException | PasswordIncorrectSemanticException e) {

            log.error("Failed to update password", e);
            request.getSession().setAttribute("error", e.getMessage());

            return "redirect:%s/%s/update-password".formatted(brandUrlMappingService.getUrlByBrand(branding), flow);

        } catch (UserUpdateFailedException | InvalidBrandingException | PasswordValidationException e) {

            log.error("Failed to update password", e);
            request.getSession().setAttribute("error", "Failed to update password");

            return "redirect:%s/%s/update-password".formatted(brandUrlMappingService.getUrlByBrand(branding), flow);

        } catch (AuthSessionNotFoundException | InvalidSessionException e) {

            throw new InvalidUserSessionException(e.getMessage());

        } catch (AccountSyncException e) {

            log.error("Failed to sync password", e);
            request.getSession().setAttribute("error", "Failed to sync password");

            return "redirect:%s/%s/update-password".formatted(brandUrlMappingService.getUrlByBrand(branding), flow);
        }
    }

    private void setIdentifyYourselfPageModelAttributes(Model model, String flow, Application application, Map<String, String> cmsData, HttpServletRequest request) {

        if (application != null) {
            UsernameTypeEnum usernameType = application.getUsernameType();
            model.addAttribute("usernameType", usernameType.toString().toLowerCase());
        }

        model.addAttribute("flow", flow);
        model.addAttribute("cancelUrl", APPLICATION_LOGIN_ENTRY_POINT_LINK);
        model.addAttribute("contact", cmsData.getOrDefault("helpContentHTML", ""));
        model.addAttribute("logo", cmsData.getOrDefault("loginLogoPath", ""));

        model.addAttribute("progressSidebarLinks", List.of(cmsData.getOrDefault("progressBarLinkHTML1", ""), cmsData.getOrDefault("progressBarLinkHTML2", ""), cmsData.getOrDefault("progressBarLinkHTML3", ""), cmsData.getOrDefault("progressBarLinkHTML4", ""), cmsData.getOrDefault("progressBarLinkHTML5", ""), cmsData.getOrDefault("progressBarLinkHTML6", "")));

        model.addAttribute("footerLinks", cmsData.getOrDefault("footerLinks", ""));
        model.addAttribute("footerHTML", cmsData.getOrDefault("footerHTML", ""));
        model.addAttribute("planPrimaryColor", cmsData.getOrDefault("planPrimaryColor", ""));
        model.addAttribute("planSecondaryColor", cmsData.getOrDefault("planSecondaryColor", ""));
        model.addAttribute("planTertiaryColor", cmsData.getOrDefault("planTertiaryColor", ""));
        model.addAttribute("planPrimaryTextColor", cmsData.getOrDefault("planPrimaryTextColor", ""));
        model.addAttribute("planSecondaryTextColor", cmsData.getOrDefault("planSecondaryTextColor", ""));
        model.addAttribute("planTertiaryTextColor", cmsData.getOrDefault("planTertiaryTextColor", ""));
        model.addAttribute("planPublicWebLink", cmsData.getOrDefault("planPublicWebLink", ""));
        model.addAttribute("backUrlPath", APPLICATION_LOGIN_ENTRY_POINT_LINK);

        model.addAttribute("planName", cmsData.getOrDefault("planName", ""));
        model.addAttribute("planFaviconPath", cmsData.getOrDefault("planFaviconPath", ""));
        model.addAttribute("forgotPasswordStep1", cmsData.getOrDefault("forgotPasswordStep1", "IDENTIFY YOURSELF (STEP 1 OR 4)"));

        if (ACTIVATE_ACCOUNT_FLOW.equals(flow)) {

            model.addAttribute("description", cmsData.getOrDefault("activateAccountContent2", ""));
            model.addAttribute("metaTitle", "Activate Account - ".concat(cmsData.getOrDefault("activateAccountNavLink1", "")));

            model.addAttribute("progressSidebarSteps", List.of(cmsData.getOrDefault("activateAccountNavLink1", ""), cmsData.getOrDefault("activateAccountNavLink2", ""), cmsData.getOrDefault("activateAccountNavLink3", ""), cmsData.getOrDefault("activateAccountNavLink4", "")));

            model.addAttribute("subtitle", cmsData.getOrDefault("activateAccountContent", ""));
            model.addAttribute("title", cmsData.getOrDefault("activateAccountTitle", ""));

        } else {

            model.addAttribute("description", cmsData.getOrDefault("forgotPasswordContent2", ""));
            model.addAttribute("metaTitle", "Forgot Password - ".concat(cmsData.getOrDefault("forgotPasswordNavLink1", "")));

            model.addAttribute("progressSidebarSteps", List.of(cmsData.getOrDefault("forgotPasswordNavLink1", ""), cmsData.getOrDefault("forgotPasswordNavLink2", ""), cmsData.getOrDefault("forgotPasswordNavLink3", ""), cmsData.getOrDefault("forgotPasswordNavLink4", "")));

            model.addAttribute("subtitle", cmsData.getOrDefault("forgotPasswordContent", ""));
            model.addAttribute("title", cmsData.getOrDefault("forgotPasswordTitle", ""));
            if (MOBILE_FORGOT_PASSWORD_FLOW.equals(flow)) {
                String branding = (String) request.getSession().getAttribute(ApplicationConstants.BRANDING_INFO);
                var redirectRootUrl = brandUrlMappingService.getUrlByBrand(branding);
                var clientId = getClientIdFromRequestSession(request);
                var hexExternalId = request.getSession().getAttribute(ApplicationConstants.HEX_EXTERNAL_ID);
                model.addAttribute("cancelUrl", "%s/%s?cid=%s&eid=%s".formatted(redirectRootUrl, MOBILE_FORGOT_USERNAME_FLOW, clientId, hexExternalId));
                model.addAttribute("cancelText", FORGOT_USERNAME_BUTTON_TEXT);
                model.addAttribute("cancelColor", FORGOT_USERNAME_BUTTON_COLOR);
                model.addAttribute("disableBack", "disableBack");
            }
        }


        if (request.getSession().getAttribute("noUserFoundError") != null) {
            model.addAttribute("error", request.getSession().getAttribute("noUserFoundError").toString());
            request.getSession().removeAttribute("noUserFoundError");
        }

        if (request.getSession().getAttribute("approvalPending") != null) {
            model.addAttribute("error", request.getSession().getAttribute("approvalPending").toString());
            request.getSession().removeAttribute("approvalPending");
        }
    }

    private void validateMobileForgotPasswordParameters(String clientId, String hexExternalId) throws MobileAccountAccessBadRequestException {

        if (StringUtils.isEmpty(clientId)) {
            throw new MobileAccountAccessBadRequestException("Missing required parameter - cid");
        }

        if (StringUtils.isEmpty(hexExternalId)) {
            throw new MobileAccountAccessBadRequestException("Missing required parameter - eid");
        }
    }

    private String getBranding(String hexExternalId) throws MobileAccountAccessBadRequestException {

        UUID externalId = UUIDUtils.oracleGuidToSqlUUID(hexExternalId);

        ExternalSource externalSource = externalSourceService.findBySourceId(externalId).orElseThrow(() -> new MobileAccountAccessBadRequestException("Invalid External Id (UUID) %s".formatted(externalId)));

        return externalSource.getSourceCode();
    }

    String getClientIdFromRequestSession(HttpServletRequest request) {
        if (request.getSession() == null) {
            return null;
        }
        return (String) request.getSession().getAttribute(ApplicationConstants.CLIENT_ID);
    }

    private String getFlow(HttpServletRequest request) {
        if (request.getRequestURI().contains("/activate-account")) {
            return ACTIVATE_ACCOUNT_FLOW;
        } else if (request.getRequestURI().contains("/mobile-forgot-password")) {
            return MOBILE_FORGOT_PASSWORD_FLOW;
        } else {
            return FORGOT_PASSWORD_FLOW;
        }
    }

    @ExceptionHandler({AppNotFoundException.class, UserNotFoundException.class})
    public String handleAppFlowExceptions(Exception ex) {
        log.error(ex.getMessage(), ex);
        return "pages/404";
    }

    @ExceptionHandler(MobileAccountAccessBadRequestException.class)
    public String handleBadRequestExceptions(Exception ex) {
        log.error(ex.getMessage(), ex);
        return "pages/404";
    }

    @ExceptionHandler({CmsBadRequestException.class, CmsProcessingException.class, CmsFileNotFoundException.class})
    public String handleCmsBadRequestException(Exception ex) {
        log.error(ex.getMessage(), ex);
        return "pages/404";
    }

}
