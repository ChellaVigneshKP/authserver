package com.chellavignesh.authserver.controller;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.MfaExpiryPinTimeService;
import com.chellavignesh.authserver.adminportal.application.entity.ApplicationDetail;
import com.chellavignesh.authserver.adminportal.application.entity.MfaExpiry;
import com.chellavignesh.authserver.adminportal.application.exception.AppNotFoundException;
import com.chellavignesh.authserver.adminportal.forgotusername.exception.InvalidUserSessionException;
import com.chellavignesh.authserver.adminportal.forgotusername.exception.UserUpdatedLoginException;
import com.chellavignesh.authserver.adminportal.globalconfig.GlobalConfigCache;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.cms.BrandUrlMappingService;
import com.chellavignesh.authserver.cms.CmsService;
import com.chellavignesh.authserver.cms.exception.CmsBadRequestException;
import com.chellavignesh.authserver.cms.exception.CmsFileNotFoundException;
import com.chellavignesh.authserver.cms.exception.CmsProcessingException;
import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.config.PreAuthGrantedAuthority;
import com.chellavignesh.authserver.enums.entity.GlobalConfigTypeEnum;
import com.chellavignesh.authserver.mfa.MFAService;
import com.chellavignesh.authserver.mfa.dto.FactorOption;
import com.chellavignesh.authserver.mfa.dto.MFAFactor;
import com.chellavignesh.authserver.mfa.dto.OtpReceiverDto;
import com.chellavignesh.authserver.mfa.dto.OtpValidationRequestDto;
import com.chellavignesh.authserver.mfa.exception.*;
import com.chellavignesh.authserver.session.AuthSessionService;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.session.exception.AuthSessionNotFoundException;
import com.chellavignesh.authserver.session.exception.MfaExpirySessionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping
@Slf4j
public class MFAController {

    private static final String LOGIN_FLOW = "login";
    private static final String FORGOT_PASSWORD_FLOW = "forgot-password";
    private static final String ACTIVATE_ACCOUNT_FLOW = "activate-account";
    private static final String MOBILE_FORGOT_PASSWORD_FLOW = "mobile-forgot-password";

    public static final String MFA_URL = "/mfa";

    private static final String INVALID_OTP_ERROR = "INVALID_OTP";
    private static final String ACCOUNT_LOCKED_ERROR = "ACCOUNT_LOCKED";

    private static final String APPLICATION_LOGIN_ENTRYPOINT_LINK = "/services/login";

    public static final String INVALID_PIN_ERROR_COUNT = "invalidPinErrorCount";

    private final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();

    private final MFAService otpService;
    private final UserService userService;
    private final CmsService cmsService;
    private final ApplicationService applicationService;
    private final AuthSessionService authSessionService;
    private final boolean userUpdatedLogin;
    private final BrandUrlMappingService brandUrlMappingService;
    private final GlobalConfigCache globalConfigCache;
    private final MfaExpiryPinTimeService mfaExpiryPinTimeService;

    @Autowired
    public MFAController(MFAService otpService, UserService userService, CmsService cmsService, ApplicationService applicationService, AuthSessionService authSessionService, @Value("${toggles.updated-login.enabled}") boolean userUpdatedLogin, BrandUrlMappingService brandUrlMappingService, GlobalConfigCache globalConfigCache, MfaExpiryPinTimeService mfaExpiryPinTimeService) {
        this.otpService = otpService;
        this.userService = userService;
        this.cmsService = cmsService;
        this.applicationService = applicationService;
        this.authSessionService = authSessionService;
        this.userUpdatedLogin = userUpdatedLogin;
        this.brandUrlMappingService = brandUrlMappingService;
        this.globalConfigCache = globalConfigCache;
        this.mfaExpiryPinTimeService = mfaExpiryPinTimeService;
    }

    /**
     * Legacy Methods
     */
    @GetMapping(value = MFA_URL)
    public String initMfaAuth() throws UserUpdatedLoginException {

        if (userUpdatedLogin) {
            throw new UserUpdatedLoginException(String.format("userUpdatedLogin value being passed as %s", userUpdatedLogin));
        }

        return "pages/mfa-legacy";
    }

    @GetMapping(value = MFA_URL, params = "error")
    public String mfaAuthWithError(Model model, @RequestParam String error) throws UserUpdatedLoginException {

        if (userUpdatedLogin) {
            throw new UserUpdatedLoginException(String.format("userUpdatedLogin value being passed as %s", true));
        }

        if (ACCOUNT_LOCKED_ERROR.equals(error)) {
            model.addAttribute("lockedAccountError", true);
        } else {
            model.addAttribute("loginError", true);
        }

        return "pages/mfa-legacy";
    }

    @PostMapping(value = MFA_URL)
    public String verifyMfaCode(@RequestParam(name = "mfa-number-1") String mfaNumber1, @RequestParam(name = "mfa-number-2") String mfaNumber2, @RequestParam(name = "mfa-number-3") String mfaNumber3, @RequestParam(name = "mfa-number-4") String mfaNumber4, @RequestParam(name = "mfa-number-5") String mfaNumber5, @RequestParam(name = "mfa-number-6") String mfaNumber6, HttpServletRequest request, HttpServletResponse response, @SessionAttribute(value = ApplicationConstants.BRANDING_INFO, required = false) String brand) throws InvalidMFAFlowException, CmsBadRequestException, CmsProcessingException, CmsFileNotFoundException, UserUpdatedLoginException, SecureAuthException {

        if (userUpdatedLogin) {
            throw new UserUpdatedLoginException(String.format("userUpdatedLogin value being passed as %s", true));
        }

        String pin = mfaNumber1 + mfaNumber2 + mfaNumber3 + mfaNumber4 + mfaNumber5 + mfaNumber6;

        return verifyMfaCode(LOGIN_FLOW, pin, request, response, brand);
    }

    @GetMapping(value = MFA_URL + "/{flow}/request-pin")
    public String initMfaAuth(HttpServletRequest request, Model model, @PathVariable String flow, @RequestParam Optional<String> error) throws InvalidUserSessionException, FactorRetrievalFailedException, CmsBadRequestException, CmsProcessingException, CmsFileNotFoundException, InvalidMFAFlowException, AppNotFoundException, AuthSessionNotFoundException {

        if (!userUpdatedLogin) {
            return "pages/mfa-legacy";
        }

        checkMFAFlow(flow);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        var sessionId = request.getSession().getAttribute(ApplicationConstants.AUTH_SESSION_ID);

        if (!isUserAuthenticated(authentication) || sessionId == null) {
            throw new InvalidUserSessionException("User session invalid or in invalid state");
        }

        Integer mfaRealmId = getRealmIdFromSession(sessionId.toString());

        List<MFAFactor> mfaFactors = otpService.getUserFactors(sessionId.toString(), mfaRealmId);

        List<FactorOption> factorOptions = getFactorOptions(mfaFactors);

        model.addAttribute("options", factorOptions);

        if (request.getSession().getAttribute("failedToSendPinError") != null) {
            model.addAttribute("error", request.getSession().getAttribute("failedToSendPinError").toString());
            request.getSession().removeAttribute("failedToSendPinError");
        }

        Map<String, String> cmsData = cmsService.getCmsInfoForRequest(request);

        getCommonMFAContent(model, flow, cmsData);

        if (error.isPresent() && error.get().equals(INVALID_OTP_ERROR)) {
            model.addAttribute("error", cmsData.getOrDefault("invalidPinError", "Invalid pin"));
        }

        model.addAttribute("planPublicWebLink", cmsData.getOrDefault("planPublicWebLink", ""));

        switch (flow) {

            case LOGIN_FLOW:
                model.addAttribute("metaTitle", "Login - ".concat(cmsData.getOrDefault("navLink2", "")));
                model.addAttribute("title", cmsData.getOrDefault("pinRequestTitle", ""));
                model.addAttribute("subtitle", cmsData.getOrDefault("pinRequestContent", ""));
                model.addAttribute("description", cmsData.getOrDefault("pinRequestContent2", ""));
                model.addAttribute("footerLinks", cmsData.getOrDefault("footerLinks", ""));
                model.addAttribute("footerHTML", cmsData.getOrDefault("footerHTML", ""));
                model.addAttribute("planPrimaryColor", cmsData.getOrDefault("planPrimaryColor", ""));
                model.addAttribute("planSecondaryColor", cmsData.getOrDefault("planSecondaryColor", ""));
                model.addAttribute("planTertiaryColor", cmsData.getOrDefault("planTertiaryColor", ""));
                model.addAttribute("planPrimaryTextColor", cmsData.getOrDefault("planPrimaryTextColor", ""));
                model.addAttribute("planSecondaryTextColor", cmsData.getOrDefault("planSecondaryTextColor", ""));
                model.addAttribute("planTertiaryTextColor", cmsData.getOrDefault("planTertiaryTextColor", ""));
                model.addAttribute("loginPinRequestStep2", cmsData.getOrDefault("loginPinRequestStep2", "PIN REQUEST (STEP 2 OF 3)"));
                break;

            case FORGOT_PASSWORD_FLOW:
                model.addAttribute("title", cmsData.getOrDefault("pinRequestTitle", ""));
                model.addAttribute("subtitle", cmsData.getOrDefault("pinRequestContent", ""));
                model.addAttribute("description", cmsData.getOrDefault("pinRequestContent2", ""));
                model.addAttribute("loginPinRequestStep2", cmsData.getOrDefault("forgotPasswordStep2", "REQUEST A PIN (STEP 2 OF 4)"));
                break;

            case MOBILE_FORGOT_PASSWORD_FLOW:
                model.addAttribute("metaTitle", "Forgot Password - ".concat(cmsData.getOrDefault("forgotPasswordNavLink2", "")));
                model.addAttribute("title", cmsData.getOrDefault("forgotPasswordRequestPinTitle", ""));
                model.addAttribute("subtitle", cmsData.getOrDefault("forgotPasswordRequestPinContent", ""));
                model.addAttribute("description", cmsData.getOrDefault("forgotPasswordRequestPinContent2", ""));
                model.addAttribute("footerLinks", cmsData.getOrDefault("footerLinks", ""));
                model.addAttribute("footerHTML", cmsData.getOrDefault("footerHTML", ""));
                model.addAttribute("planPrimaryColor", cmsData.getOrDefault("planPrimaryColor", ""));
                model.addAttribute("planSecondaryColor", cmsData.getOrDefault("planSecondaryColor", ""));
                model.addAttribute("planTertiaryColor", cmsData.getOrDefault("planTertiaryColor", ""));
                model.addAttribute("planPrimaryTextColor", cmsData.getOrDefault("planPrimaryTextColor", ""));
                model.addAttribute("planSecondaryTextColor", cmsData.getOrDefault("planSecondaryTextColor", ""));
                model.addAttribute("planTertiaryTextColor", cmsData.getOrDefault("planTertiaryTextColor", ""));
                model.addAttribute("loginPinRequestStep2", cmsData.getOrDefault("forgotPasswordStep2", "REQUEST A PIN (STEP 2 OF 4)"));
                break;
            case ACTIVATE_ACCOUNT_FLOW:
                model.addAttribute("metaTitle", "Activate Account - ".concat(cmsData.getOrDefault("activateAccountNavLink2", "")));
                model.addAttribute("title", cmsData.getOrDefault("activateAccountRequestPinTitle", ""));
                model.addAttribute("subtitle", cmsData.getOrDefault("activateAccountRequestPinContent", ""));
                model.addAttribute("description", cmsData.getOrDefault("activateAccountRequestPinContent2", ""));
                model.addAttribute("footerLinks", cmsData.getOrDefault("footerLinks", ""));
                model.addAttribute("footerHTML", cmsData.getOrDefault("footerHTML", ""));
                model.addAttribute("planPrimaryColor", cmsData.getOrDefault("planPrimaryColor", ""));
                model.addAttribute("planSecondaryColor", cmsData.getOrDefault("planSecondaryColor", ""));
                model.addAttribute("planTertiaryColor", cmsData.getOrDefault("planTertiaryColor", ""));
                model.addAttribute("planPrimaryTextColor", cmsData.getOrDefault("planPrimaryTextColor", ""));
                model.addAttribute("planSecondaryTextColor", cmsData.getOrDefault("planSecondaryTextColor", ""));
                model.addAttribute("planTertiaryTextColor", cmsData.getOrDefault("planTertiaryTextColor", ""));
                break;
            default:
                break;
        }

        return "pages/mfa/request-pin";
    }

    @PostMapping(value = MFA_URL + "/{flow}/request-pin")
    public String requestPin(@RequestParam(name = "option") String option, @PathVariable(name = "flow") String flow, HttpServletRequest request, @SessionAttribute(value = ApplicationConstants.BRANDING_INFO, required = false) String brand) throws InvalidMFAFlowException, CmsBadRequestException, CmsProcessingException, CmsFileNotFoundException, InvalidUserSessionException {

        checkMFAFlow(flow);

        if (!isSessionPresent(request)) {
            throw new InvalidUserSessionException("User session invalid or in invalid state");
        }

        String[] factorIdentifier = option.split("-");

        resetInvalidPinErrorCount(request);

        try {
            var sessionId = request.getSession().getAttribute(ApplicationConstants.AUTH_SESSION_ID);

            Integer mfaRealmId = getRealmIdFromSession(sessionId.toString());

            otpService.generateAndSendOTPCode(new OtpReceiverDto(sessionId.toString(), factorIdentifier[1], factorIdentifier[0]), mfaRealmId);

            return "redirect:%s/mfa/%s/verify-pin".formatted(brandUrlMappingService.getUrlByBrand(brand), flow);

        } catch (Exception e) {
            String message = "Failed to generate MFA OTP code";
            log.error(message, e);
            var cmsData = cmsService.getCmsInfoForRequest(request);
            request.getSession().setAttribute("failedToSendPinError", cmsData.getOrDefault("failedToSendPinError", null));
        }

        return "redirect:%s/mfa/%s/request-pin".formatted(brandUrlMappingService.getUrlByBrand(brand), flow);
    }


    @GetMapping(value = MFA_URL + "/{flow}/verify-pin")
    public String promptForPin(@PathVariable(name = "flow") String flow, Model model, HttpServletRequest request) throws CmsBadRequestException, CmsProcessingException, CmsFileNotFoundException, InvalidMFAFlowException, AuthSessionNotFoundException, InvalidUserSessionException, AppNotFoundException, MfaExpirySessionException {

        checkMFAFlow(flow);

        var cmsData = cmsService.getCmsInfoForRequest(request);
        getCommonMFAContent(model, flow, cmsData);

        if (request.getSession().getAttribute("error") != null) {
            model.addAttribute("error", request.getSession().getAttribute("error"));
            request.getSession().removeAttribute("error");
        }

        var sessionId = request.getSession().getAttribute(ApplicationConstants.AUTH_SESSION_ID);

        if (sessionId == null) {
            throw new InvalidUserSessionException("User session invalid or in invalid state");
        }

        Optional<AuthSession> optionalAuthSession = authSessionService.getBySessionId(UUID.fromString(sessionId.toString()));

        if (optionalAuthSession.isEmpty()) {
            throw new AuthSessionNotFoundException("Unable to locate auth session to send the user MFA code.");
        }

        AuthSession authSession = optionalAuthSession.get();

        Optional<ApplicationDetail> applicationDetailOptional = applicationService.getApplicationDetailById(authSession.getApplicationId());

        if (applicationDetailOptional.isEmpty()) {
            throw new AppNotFoundException("Application not found in the AuthSession.");
        }

        Integer pinTimeToLiveInt = applicationDetailOptional.get().getPinTimeToLive();

        MfaExpiry mfaExpiryPinTime = mfaExpiryPinTimeService.getMfaExpiryPinTime(pinTimeToLiveInt, UUID.fromString(sessionId.toString()));

        if (mfaExpiryPinTime.getTimeToExpireMS() == null) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            mfaExpiryPinTime = mfaExpiryPinTimeService.getMfaExpiryPinTime(pinTimeToLiveInt, UUID.fromString(sessionId.toString()));

        } else if (mfaExpiryPinTime.getTimeToExpireMS() == -1) {
            mfaExpiryPinTime.setTimeToExpireMS(0L);
        }

        if (mfaExpiryPinTime.getTimeToExpireMS() != null) {

            String pinTimeToLive = Long.toString(mfaExpiryPinTime.getTimeToExpireMS());

            String redirectToRequestPinLink = "/services/mfa/%s/request-pin".formatted(flow);

            int pinTimeToLiveMinutes = Integer.parseInt(pinTimeToLive) / 60;

            model.addAttribute("mfaExpiryPinTime", pinTimeToLive);
            model.addAttribute("mfaPinExpiryModalHeader", cmsData.getOrDefault("mfaPinExpiryModalHeader", ""));
            model.addAttribute("mfaPinExpiryModalBody", cmsData.getOrDefault("mfaPinExpiryModalBody", "").replace("{{pinTimeToLive}}", Integer.toString(pinTimeToLiveMinutes)));
            model.addAttribute("mfaExpiryModalLink", redirectToRequestPinLink);
            model.addAttribute("mfaPinExpiryModalLinkText", cmsData.getOrDefault("mfaPinExpiryModalLinkText", ""));

        } else {
            throw new MfaExpirySessionException("MFA Expiry Pin failed for this session.");
        }

        model.addAttribute("planPublicWebLink", cmsData.getOrDefault("planPublicWebLink", ""));

        switch (flow) {

            case LOGIN_FLOW:
                model.addAttribute("confirmText", "Log In");
                model.addAttribute("description", "");
                model.addAttribute("metaTitle", "Login - ".concat(cmsData.getOrDefault("navLink3", "")));
                model.addAttribute("subtitle", cmsData.getOrDefault("pinEntryContent", ""));
                model.addAttribute("title", cmsData.getOrDefault("pinEntryTitle", ""));
                model.addAttribute("footerLinks", cmsData.getOrDefault("footerLinks", ""));
                model.addAttribute("footerHTML", cmsData.getOrDefault("footerHTML", ""));
                model.addAttribute("planPrimaryColor", cmsData.getOrDefault("planPrimaryColor", ""));
                model.addAttribute("planSecondaryColor", cmsData.getOrDefault("planSecondaryColor", ""));
                model.addAttribute("planTertiaryColor", cmsData.getOrDefault("planTertiaryColor", ""));
                model.addAttribute("planPrimaryTextColor", cmsData.getOrDefault("planPrimaryTextColor", ""));
                model.addAttribute("planSecondaryTextColor", cmsData.getOrDefault("planSecondaryTextColor", ""));
                model.addAttribute("planTertiaryTextColor", cmsData.getOrDefault("planTertiaryTextColor", ""));
                model.addAttribute("loginPinAuthStep3", cmsData.getOrDefault("loginPinAuthStep3", "PIN AUTHENTICATION (STEP 3 OF 3)"));
                break;

            case FORGOT_PASSWORD_FLOW:
                model.addAttribute("title", cmsData.getOrDefault("pinEntryTitle", ""));
                model.addAttribute("subtitle", cmsData.getOrDefault("pinEntryContent", ""));
                model.addAttribute("description", "");
                model.addAttribute("loginPinAuthStep3", cmsData.getOrDefault("forgotPasswordStep3", "ENTER PIN (STEP 3 OF 4)"));
                break;

            case MOBILE_FORGOT_PASSWORD_FLOW:
                model.addAttribute("confirmText", "Next");
                model.addAttribute("description", cmsData.getOrDefault("forgotPasswordEnterPinContent2", ""));
                model.addAttribute("metaTitle", "Forgot Password - ".concat(cmsData.getOrDefault("forgotPasswordNavLink3", "")));
                model.addAttribute("subtitle", cmsData.getOrDefault("forgotPasswordEnterPinContent", ""));
                model.addAttribute("title", cmsData.getOrDefault("forgotPasswordEnterPinTitle", ""));
                model.addAttribute("footerLinks", cmsData.getOrDefault("footerLinks", ""));
                model.addAttribute("footerHTML", cmsData.getOrDefault("footerHTML", ""));
                model.addAttribute("planPrimaryColor", cmsData.getOrDefault("planPrimaryColor", ""));
                model.addAttribute("planSecondaryColor", cmsData.getOrDefault("planSecondaryColor", ""));
                model.addAttribute("planTertiaryColor", cmsData.getOrDefault("planTertiaryColor", ""));
                model.addAttribute("planPrimaryTextColor", cmsData.getOrDefault("planPrimaryTextColor", ""));
                model.addAttribute("planSecondaryTextColor", cmsData.getOrDefault("planSecondaryTextColor", ""));
                model.addAttribute("planTertiaryTextColor", cmsData.getOrDefault("planTertiaryTextColor", ""));
                model.addAttribute("loginPinAuthStep3", cmsData.getOrDefault("forgotPasswordStep3", "ENTER PIN (STEP 3 OF 4)"));
                break;

            case ACTIVATE_ACCOUNT_FLOW:
                model.addAttribute("confirmText", "Next");
                model.addAttribute("description", cmsData.getOrDefault("activateAccountEnterPinContent2", ""));
                model.addAttribute("metaTitle", "Activate Account - ".concat(cmsData.getOrDefault("activateAccountNavLink3", "")));
                model.addAttribute("subtitle", cmsData.getOrDefault("activateAccountEnterPinContent", ""));
                model.addAttribute("title", cmsData.getOrDefault("activateAccountEnterPinTitle", ""));
                model.addAttribute("footerLinks", cmsData.getOrDefault("footerLinks", ""));
                model.addAttribute("footerHTML", cmsData.getOrDefault("footerHTML", ""));
                model.addAttribute("planPrimaryColor", cmsData.getOrDefault("planPrimaryColor", ""));
                model.addAttribute("planSecondaryColor", cmsData.getOrDefault("planSecondaryColor", ""));
                model.addAttribute("planTertiaryColor", cmsData.getOrDefault("planTertiaryColor", ""));
                model.addAttribute("planPrimaryTextColor", cmsData.getOrDefault("planPrimaryTextColor", ""));
                model.addAttribute("planSecondaryTextColor", cmsData.getOrDefault("planSecondaryTextColor", ""));
                model.addAttribute("planTertiaryTextColor", cmsData.getOrDefault("planTertiaryTextColor", ""));
                break;
            default:
                break;
        }

        return "pages/mfa/enter-pin";
    }

    @PostMapping(value = MFA_URL + "/{flow}/verify-pin")
    public String verifyMfaCode(@PathVariable(name = "flow") String flow, @RequestParam(name = "pin") String pin, HttpServletRequest request, HttpServletResponse response, @SessionAttribute(value = ApplicationConstants.BRANDING_INFO, required = false) String brand) throws InvalidMFAFlowException, CmsBadRequestException, CmsProcessingException, CmsFileNotFoundException, SecureAuthException {

        checkMFAFlow(flow);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String sessionId = (String) request.getSession().getAttribute(ApplicationConstants.AUTH_SESSION_ID);

        if (!isUserAuthenticated(authentication) || sessionId == null) {
            // User not logged in, redirect to login page
            return "redirect:%s/login".formatted(brandUrlMappingService.getUrlByBrand(brand));
        }

        try {
            Integer mfaRealmId = getRealmIdFromSession(sessionId);
            otpService.validateMFACode(new OtpValidationRequestDto(sessionId, pin), mfaRealmId);

        } catch (InvalidMFACodeException | AuthSessionNotFoundException | AppNotFoundException |
                 MFARealmNotFoundException e) {

            var cmsData = cmsService.getCmsInfoForRequest(request);

            if (flow.equals(LOGIN_FLOW)) {

                if (hasReachedMaxInvalidPinFailures(request)) {
                    resetInvalidPinErrorCount(request);

                    if (!userUpdatedLogin) {
                        return "redirect:%s/mfa/error/%s".formatted(brandUrlMappingService.getUrlByBrand(brand), ACCOUNT_LOCKED_ERROR);
                    }

                    return "redirect:%s/mfa/%s/request-pin?error=%s".formatted(brandUrlMappingService.getUrlByBrand(brand), flow, INVALID_OTP_ERROR);
                } else {
                    if (!userUpdatedLogin) {
                        return "redirect:%s/mfa/error/%s".formatted(brandUrlMappingService.getUrlByBrand(brand), INVALID_OTP_ERROR);
                    }
                }
            }

            request.getSession().setAttribute("error", cmsData.getOrDefault("invalidPinError", "Invalid Pin"));

            return "redirect:%s/mfa/%s/verify-pin".formatted(brandUrlMappingService.getUrlByBrand(brand), flow);
        }

        switch (flow) {

            case LOGIN_FLOW:
                userService.updateAccessFailedCountWithExternalSourceCode(authentication.getName(), brand, 1);

                updateAuthentication(authentication, true);

                SavedRequest savedRequest = requestCache.getRequest(request, response);

                log.warn("Request Session Auth Session Id Verify Pin: {}", request.getSession().getAttribute(ApplicationConstants.AUTH_SESSION_ID).toString());
                log.warn("Request Session Id Verify Pin: {}", request.getSession().getId());

                if (savedRequest != null && StringUtils.isNotEmpty(savedRequest.getRedirectUrl())) {

                    String targetUrl = spliceBrandedUrl(request.getSession(), savedRequest.getRedirectUrl());

                    return "redirect:%s".formatted(targetUrl);
                } else {
                    throw new InvalidMFAFlowException("Saved request does not contain a valid redirect url");
                }

            case FORGOT_PASSWORD_FLOW:
                return "redirect:%s/forgot-password/update-password".formatted(brandUrlMappingService.getUrlByBrand(brand));

            case MOBILE_FORGOT_PASSWORD_FLOW:
                return "redirect:%s/mobile-forgot-password/update-password".formatted(brandUrlMappingService.getUrlByBrand(brand));

            case ACTIVATE_ACCOUNT_FLOW:
                return "redirect:%s/activate-account/update-password".formatted(brandUrlMappingService.getUrlByBrand(brand));
            default:
                throw new InvalidMFAFlowException(flow + " not supported for MFA code validation");
        }
    }


    boolean isSessionPresent(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Object sessionId = request.getSession().getAttribute(ApplicationConstants.AUTH_SESSION_ID);

        return !(sessionId == null || authentication == null);
    }


    boolean isUserAuthenticated(Authentication authentication) {
        return authentication.isAuthenticated() || authentication.getAuthorities().contains(new PreAuthGrantedAuthority());
    }


    void updateAuthentication(Authentication authentication, boolean isAuthenticated) {
        // Remove temporary granted authority from the Authorization
        List<GrantedAuthority> updatedAuthorities = authentication.getAuthorities().stream().filter(authority -> !authority.equals(new PreAuthGrantedAuthority())).collect(Collectors.toList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        Authentication newAuth;

        if (isAuthenticated) {
            newAuth = new UsernamePasswordAuthenticationToken(authentication.getPrincipal(), authentication.getCredentials(), updatedAuthorities);
        } else {
            // Flags session as unauthenticated
            newAuth = new UsernamePasswordAuthenticationToken(authentication.getPrincipal(), authentication.getCredentials());
        }

        SecurityContextHolder.getContext().setAuthentication(newAuth);
    }


    List<FactorOption> getFactorOptions(List<MFAFactor> mfaFactors) {

        return mfaFactors.stream().flatMap(mfaFactor -> mfaFactor.getCapabilities().stream().map(capability -> {

            String text;
            String target;

            if (mfaFactor.getType().equals("phone")) {
                target = "XXX-XXX-" + mfaFactor.getValue().substring(mfaFactor.getValue().length() - 4);
            } else {
                target = mfaFactor.getValue();
            }

            text = switch (capability) {
                case "sms" -> "Text to %s".formatted(target);
                case "call" -> "Voice call to %s".formatted(target);
                case "email" -> "Email to %s".formatted(target);
                default -> "Send code to %s".formatted(target);
            };

            return new FactorOption(mfaFactor.getId() + "-" + capability, text);
        })).collect(Collectors.toList());
    }

    private void checkMFAFlow(String flow) throws InvalidMFAFlowException {
        if (!Arrays.asList(LOGIN_FLOW, FORGOT_PASSWORD_FLOW, ACTIVATE_ACCOUNT_FLOW, MOBILE_FORGOT_PASSWORD_FLOW).contains(flow)) {
            throw new InvalidMFAFlowException("%s is not a valid MFA Flow".formatted(flow));
        }
    }


    private void getCommonMFAContent(Model model, String flow, Map<String, String> cmsData) {

        model.addAttribute("cancelUrl", APPLICATION_LOGIN_ENTRYPOINT_LINK);
        model.addAttribute("contact", cmsData.getOrDefault("helpContentHTML", ""));
        model.addAttribute("copyright", cmsData.getOrDefault("copyright", ""));
        model.addAttribute("requestAnotherPin", cmsData.getOrDefault("requestAnotherPin", ""));
        // Add planName and planFaviconPath
        model.addAttribute("planName", cmsData.getOrDefault("planName", ""));
        model.addAttribute("planFaviconPath", cmsData.getOrDefault("planFaviconPath", ""));

        model.addAttribute("flow", flow);
        model.addAttribute("logo", cmsData.getOrDefault("loginLogoPath", ""));

        model.addAttribute("progressSidebarLinks", List.of(cmsData.getOrDefault("progressBarLinkHTML1", ""), cmsData.getOrDefault("progressBarLinkHTML2", ""), cmsData.getOrDefault("progressBarLinkHTML3", ""), cmsData.getOrDefault("progressBarLinkHTML4", ""), cmsData.getOrDefault("progressBarLinkHTML5", ""), cmsData.getOrDefault("progressBarLinkHTML6", "")));

        model.addAttribute("backUrlPath", APPLICATION_LOGIN_ENTRYPOINT_LINK);

        switch (flow.trim().toLowerCase()) {

            case LOGIN_FLOW:
                model.addAttribute("progressSidebarSteps", List.of(cmsData.getOrDefault("navLink1", ""), cmsData.getOrDefault("navLink2", ""), cmsData.getOrDefault("navLink3", "")));
                break;

            case FORGOT_PASSWORD_FLOW:
                model.addAttribute("progressSidebarSteps", List.of(cmsData.getOrDefault("forgotPasswordNavLink1", ""), cmsData.getOrDefault("forgotPasswordNavLink2", ""), cmsData.getOrDefault("forgotPasswordNavLink3", ""), cmsData.getOrDefault("forgotPasswordNavLink4", "")));
                break;

            case MOBILE_FORGOT_PASSWORD_FLOW:
                model.addAttribute("progressSidebarSteps", List.of(cmsData.getOrDefault("forgotPasswordNavLink1", ""), cmsData.getOrDefault("forgotPasswordNavLink2", ""), cmsData.getOrDefault("forgotPasswordNavLink3", ""), cmsData.getOrDefault("forgotPasswordNavLink4", "")));
                // cancel button hidden for Mobile forgot password flow
                model.addAttribute("disableCancel", "disableCancel");
                break;

            case ACTIVATE_ACCOUNT_FLOW:
                model.addAttribute("progressSidebarSteps", List.of(cmsData.getOrDefault("activateAccountNavLink1", ""), cmsData.getOrDefault("activateAccountNavLink2", ""), cmsData.getOrDefault("activateAccountNavLink3", ""), cmsData.getOrDefault("activateAccountNavLink4", "")));
                break;
            default:
                break;
        }
    }


    private boolean hasReachedMaxInvalidPinFailures(HttpServletRequest request) {
        Integer invalidPinErrorCount = (Integer) request.getSession().getAttribute(INVALID_PIN_ERROR_COUNT);

        if (invalidPinErrorCount == null) {
            invalidPinErrorCount = 0;
        }

        invalidPinErrorCount++;

        request.getSession().setAttribute(INVALID_PIN_ERROR_COUNT, invalidPinErrorCount);

        return invalidPinErrorCount >= getMaxMfaPinFailureCount();
    }


    private Integer getMaxMfaPinFailureCount() {
        return globalConfigCache.getGlobalConfig(GlobalConfigTypeEnum.MAX_MFA_PIN_FAILURE_COUNT);
    }


    void resetInvalidPinErrorCount(HttpServletRequest request) {
        request.getSession().removeAttribute(INVALID_PIN_ERROR_COUNT);
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


    Integer getRealmIdFromSession(String sessionId) throws AuthSessionNotFoundException, AppNotFoundException {

        // Get application from the session, in order to get the MFA realm ID
        Optional<AuthSession> optionalAuthSession = authSessionService.getBySessionId(UUID.fromString(sessionId));

        if (optionalAuthSession.isEmpty()) {
            throw new AuthSessionNotFoundException("Unable to locate auth session to send the user MFA code.");
        }

        AuthSession session = optionalAuthSession.get();

        Optional<ApplicationDetail> applicationDetailOptional = applicationService.getApplicationDetailById(session.getApplicationId());

        if (applicationDetailOptional.isEmpty()) {
            throw new AppNotFoundException("Application not found in the AuthSession.");
        }

        return applicationDetailOptional.get().getMfaRealmId();
    }


    @ExceptionHandler({CmsBadRequestException.class, CmsProcessingException.class, CmsFileNotFoundException.class, InvalidMFAFlowException.class})
    public String handleBadRequestException(Exception e) {
        return "pages/404";
    }


    @ExceptionHandler({UserUpdatedLoginException.class, SecureAuthException.class})
    public String handleUserApplicationConditionalException() {
        return "pages/404";
    }


    @ExceptionHandler(InvalidUserSessionException.class)
    public String handleInvalidUserSessionException(InvalidUserSessionException e) {
        log.error("Invalid user session in MFA Flow: {}", e.getMessage());
        return "pages/404";
    }

}

