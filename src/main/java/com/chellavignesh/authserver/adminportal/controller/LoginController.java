package com.chellavignesh.authserver.adminportal.controller;

import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.application.exception.AppNotFoundException;
import com.chellavignesh.authserver.adminportal.forgotcredentials.ForgotCredentialsService;
import com.chellavignesh.authserver.cms.BrandUrlMappingService;
import com.chellavignesh.authserver.cms.CmsService;
import com.chellavignesh.authserver.cms.exception.CmsBadRequestException;
import com.chellavignesh.authserver.cms.exception.CmsFileNotFoundException;
import com.chellavignesh.authserver.cms.exception.CmsProcessingException;
import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.enums.entity.UsernameTypeEnum;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.tomcat.util.descriptor.web.Constants.COOKIE_SAME_SITE_ATTR;

@Controller
public class LoginController {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    private final CmsService cmsService;
    private final boolean useUpdateLogin;
    private final ForgotCredentialsService forgotPasswordService;
    private final BrandUrlMappingService brandUrlMappingService;

    @Value("${toggles.forgot.username.enable:false}")
    private boolean isForgotUsernameFeatureEnabled;

    @Value("#{'${base-url.added}'.split(',')}")
    private List<String> addBaseURLList;

    @Value("${toggles.biometric.enabled}")
    private boolean isBiometricEnabled;

    public LoginController(CmsService cmsService, @Value("${toggles.updated-login.enabled}") boolean useUpdateLogin, ForgotCredentialsService forgotPasswordService, BrandUrlMappingService brandUrlMappingService) {
        this.cmsService = cmsService;
        this.useUpdateLogin = useUpdateLogin;
        this.forgotPasswordService = forgotPasswordService;
        this.brandUrlMappingService = brandUrlMappingService;
    }

    // ---------------------------------------------------------
    // LOGIN ERROR
    // ---------------------------------------------------------

    @GetMapping(value = "/login", params = "error")
    public String loginError(Model model, HttpServletRequest request, HttpServletResponse response) throws CmsBadRequestException, CmsFileNotFoundException, CmsProcessingException, AppNotFoundException {

        boolean lockedAccount = Boolean.TRUE.equals(request.getSession().getAttribute("lockedAccountError"));

        boolean biometricError = Boolean.TRUE.equals(request.getSession().getAttribute(ApplicationConstants.BIOMETRIC_AUTH_ERROR));

        if (lockedAccount) {
            model.addAttribute("lockedAccountError", true);
        } else if (biometricError) {
            model.addAttribute("biometricError", true);
        } else {
            model.addAttribute("loginError", true);
        }

        if (!useUpdateLogin) {
            return "pages/login-legacy";
        }

        var cmsData = cmsService.getCmsInfoForRequest(request);

        if (lockedAccount) {
            request.getSession().setAttribute("error", cmsData.getOrDefault("lockedOutError", ""));
            request.getSession().removeAttribute("lockedAccountError");
        } else if (biometricError) {
            String errorKey = (String) request.getSession().getAttribute("error");
            request.getSession().setAttribute("error", cmsData.getOrDefault(errorKey, ""));
            request.getSession().removeAttribute(ApplicationConstants.BIOMETRIC_AUTH_ERROR);
        } else {
            request.getSession().setAttribute("error", cmsData.getOrDefault("invalidLoginError", ""));
            request.getSession().removeAttribute("loginError");
        }

        return login(model, request, response);
    }

    // ---------------------------------------------------------
    // HIGH RISK LOGIN ERROR
    // ---------------------------------------------------------

    @GetMapping(value = "/login", params = "errorMessage")
    public String loginErrorMessage(Model model, HttpServletRequest request, HttpServletResponse response) throws CmsBadRequestException, CmsFileNotFoundException, CmsProcessingException, AppNotFoundException {

        model.addAttribute("highRiskLoginError", true);
        var cmsData = cmsService.getCmsInfoForRequest(request);

        request.getSession().setAttribute("errorMessage", cmsData.getOrDefault("highRiskLoginError", ""));

        return login(model, request, response);
    }

    // ---------------------------------------------------------
    // LOGIN PAGE
    // ---------------------------------------------------------

    @GetMapping("/login")
    public String login(Model model, HttpServletRequest request, HttpServletResponse response) throws CmsBadRequestException, CmsFileNotFoundException, CmsProcessingException, AppNotFoundException {

        if (!useUpdateLogin) {
            return "pages/login-legacy";
        }

        // Extract branding and clientId from saved authorize request if missing from session
        restoreSessionAttributesFromSavedRequest(request);

        String clientId = getClientIdFromRequestSession(request);
        var cmsData = cmsService.getCmsInfoForRequest(request);

        // Add plan info
        model.addAttribute("planName", cmsData.getOrDefault("planName", ""));
        model.addAttribute("planFaviconPath", cmsData.getOrDefault("planFaviconPath", ""));

        // Append p parameter to CMS URLs
        String p = (String) request.getSession().getAttribute(ApplicationConstants.P_VALUE);

        for (String parameter : addBaseURLList) {
            if (cmsData.containsKey(parameter)) {
                String newValue = appendPParameter(cmsData.get(parameter), p);
                cmsData.put(parameter, newValue);
            }
        }

        Application application = forgotPasswordService.checkClientId(clientId).get();

        if (application != null) {
            UsernameTypeEnum usernameType = application.getUsernameType();

            boolean allowForgotUsername = isForgotUsernameFeatureEnabled && application.getAllowForgotUsername();

            model.addAttribute("usernameType", usernameType.toString().toLowerCase());
            model.addAttribute("allowForgotUsername", allowForgotUsername);
        }

        // Success message
        if (request.getSession().getAttribute("success") != null) {
            model.addAttribute("success", request.getSession().getAttribute("success"));
            request.getSession().removeAttribute("success");
        }

        // Error handling
        if (request.getSession().getAttribute("error") != null) {
            model.addAttribute("error", request.getSession().getAttribute("error"));
            request.getSession().removeAttribute("error");
        } else if (request.getSession().getAttribute(ApplicationConstants.EXTERNAL_AUTH_ERROR_CODE) != null) {

            String errorMessage = cmsData.getOrDefault(request.getSession().getAttribute(ApplicationConstants.EXTERNAL_AUTH_ERROR_CODE), null);
            model.addAttribute("error", errorMessage);

        } else if (request.getSession().getAttribute("errorMessage") != null) {
            model.addAttribute("error", request.getSession().getAttribute("errorMessage"));
            request.getSession().removeAttribute("errorMessage");
        }

        // Activate account
        String activateAccountTitle = cmsData.getOrDefault("activateAccountTitle", null);

        if (activateAccountTitle != null) {
            model.addAttribute("allowActivateAccount", true);
            model.addAttribute("activateAccountTitle", activateAccountTitle);
        } else {
            model.addAttribute("allowActivateAccount", false);
        }

        // Clear username lookup priority
        if (request.getSession().getAttribute(ApplicationConstants.USERNAME_LOOKUP_PRIORITY) != null) {
            request.getSession().removeAttribute(ApplicationConstants.USERNAME_LOOKUP_PRIORITY);
        }

        // Biometric
        String biometricType = getBiometricTypeFromRequestSession(request);

        logger.info("isBiometricEnabled flag value {}", isBiometricEnabled);
        logger.info("biometricType flag value {}", biometricType);

        if (biometricType != null) {
            model.addAttribute("isBiometricEnabled", isBiometricEnabled);
            model.addAttribute("biometricType", biometricType);
        }

        // Cookie cleanup
        String branding = (String) request.getSession().getAttribute(ApplicationConstants.BRANDING_INFO);

        Cookie cookie = new Cookie(ApplicationConstants.SSO_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        cookie.setSecure(true);
        cookie.setAttribute(COOKIE_SAME_SITE_ATTR, "strict");
        cookie.setDomain(brandUrlMappingService.getUrlByBrand(branding).getHost());
        response.addCookie(cookie);

        // Banner
        if (!cmsData.getOrDefault("loginBannerContent", "").isEmpty()) {
            model.addAttribute("banner", Map.of("content", cmsData.getOrDefault("loginBannerContent", ""), "cta", Map.of("text", cmsData.getOrDefault("loginBannerCTA", ""), "url", cmsData.getOrDefault("loginBannerCTALink", "")), "icon", cmsData.getOrDefault("loginBannerIconPath", ""), "title", cmsData.getOrDefault("loginBannerTitle", "")));
        }

        // Page content
        model.addAttribute("contact", cmsData.getOrDefault("helpContentHTML", ""));
        model.addAttribute("loginBelowFormHTML", cmsData.getOrDefault("loginBelowFormHTML", ""));
        model.addAttribute("formTitle", cmsData.getOrDefault("introTitle", ""));
        model.addAttribute("loginBannerHTML", cmsData.getOrDefault("loginBannerHTML", ""));

        // Intro content
        if (!branding.contains("able") || (application.getCmsContext() != null && application.getCmsContext().equals("entity"))) {

            model.addAttribute("intro", Map.of("items", List.of(cmsData.getOrDefault("loginContent1", ""), cmsData.getOrDefault("loginContent2", ""), cmsData.getOrDefault("loginContent3", "")), "logo", cmsData.getOrDefault("loginLogoPath", ""), "summary", cmsData.getOrDefault("loginContentA", ""), "title", cmsData.getOrDefault("loginTitleA", "")));
        } else {
            model.addAttribute("intro", Map.of("items", List.of(cmsData.getOrDefault("loginContent1", ""), cmsData.getOrDefault("loginContent2", ""), cmsData.getOrDefault("loginContent3", ""), cmsData.getOrDefault("loginContent4", "")), "logo", cmsData.getOrDefault("loginLogoPath", ""), "summary", cmsData.getOrDefault("loginContentA", ""), "title", cmsData.getOrDefault("loginTitleA", "")));
        }

        // Footer
        model.addAttribute("footerLinks", cmsData.getOrDefault("footerLinks", ""));
        model.addAttribute("footerHTML", cmsData.getOrDefault("footerHTML", ""));
        model.addAttribute("planPrimaryColor", cmsData.getOrDefault("planPrimaryColor", ""));
        model.addAttribute("planSecondaryColor", cmsData.getOrDefault("planSecondaryColor", ""));
        model.addAttribute("planTertiaryColor", cmsData.getOrDefault("planTertiaryColor", ""));
        model.addAttribute("planPrimaryTextColor", cmsData.getOrDefault("planPrimaryTextColor", ""));
        model.addAttribute("planSecondaryTextColor", cmsData.getOrDefault("planSecondaryTextColor", ""));
        model.addAttribute("planTertiaryTextColor", cmsData.getOrDefault("planTertiaryTextColor", ""));
        model.addAttribute("planPublicWebLink", cmsData.getOrDefault("planPublicWebLink", ""));

        return "pages/login/login";
    }

    public String appendPParameter(String htmlString, String p) {
        if (htmlString == null || p == null || !containsBurlInPParameter(p)) {
            return htmlString;
        }

        // Regular expression to find href attribute
        Pattern pattern = Pattern.compile("href=['\"](.*?)['\"]");
        Matcher matcher = pattern.matcher(htmlString);
        String pParameter = String.format("p=%s", p);

        if (matcher.find()) {
            String url = matcher.group(1);
            String separator = url.contains("?") ? "&" : "?";
            url = url + separator + pParameter;

            return htmlString.substring(0, matcher.start()) + "href=\"" + url + "\"" + htmlString.substring(matcher.end());
        }

        return htmlString;
    }

    public boolean containsBurlInPParameter(String p) {
        byte[] decoded = Base64.getDecoder().decode(p);
        String decodedStr = new String(decoded, StandardCharsets.UTF_8);

        String[] splitStr = decodedStr.split("&");
        Map<String, String> parameters = new HashMap<>();

        for (String str : splitStr) {
            String[] param = str.split("=", 2);
            if (param.length == 2) {
                String key = URLDecoder.decode(param[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(param[1], StandardCharsets.UTF_8);
                parameters.put(key, value);
            }
        }

        return parameters.containsKey("burl");
    }

    private void restoreSessionAttributesFromSavedRequest(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) return;

        // If branding is already in session, nothing to do
        if (session.getAttribute(ApplicationConstants.BRANDING_INFO) != null) return;

        // Try to extract from the saved authorize request that Spring stored before redirecting here
        SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, null);
        if (savedRequest == null) return;

        Map<String, String[]> params = savedRequest.getParameterMap();

        String[] brandingParams = params.get(ApplicationConstants.BRANDING_INFO);
        if (brandingParams != null && brandingParams.length > 0) {
            session.setAttribute(ApplicationConstants.BRANDING_INFO, brandingParams[0]);
            logger.debug("Restored branding '{}' from saved authorize request", brandingParams[0]);
        }

        if (session.getAttribute(ApplicationConstants.CLIENT_ID) == null) {
            String[] clientIdParams = params.get("client_id");
            if (clientIdParams != null && clientIdParams.length > 0) {
                session.setAttribute(ApplicationConstants.CLIENT_ID, clientIdParams[0]);
                logger.debug("Restored clientId from saved authorize request");
            }
        }
    }

    String getClientIdFromRequestSession(HttpServletRequest request) {
        if (request.getSession() == null) {
            return null;
        }
        return (String) request.getSession().getAttribute(ApplicationConstants.CLIENT_ID);
    }

    String getBiometricTypeFromRequestSession(HttpServletRequest request) {
        if (request.getSession() == null) {
            return null;
        }
        return (String) request.getSession().getAttribute(ApplicationConstants.BIOMETRIC_TYPE);
    }

    @ExceptionHandler({CmsBadRequestException.class, CmsProcessingException.class, CmsFileNotFoundException.class})
    public String handleBadRequestException(Exception e) {
        return "pages/404";
    }

    @ExceptionHandler({AppNotFoundException.class})
    public String handleAppNotFoundException(AppNotFoundException e) {
        return "pages/404";
    }
}

