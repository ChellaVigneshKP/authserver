package com.chellavignesh.authserver.adminportal.controller;

import com.chellavignesh.authserver.adminportal.application.dto.UsernameLookupField;
import com.chellavignesh.authserver.adminportal.application.entity.ApplicationDetail;
import com.chellavignesh.authserver.adminportal.application.exception.AppNotFoundException;
import com.chellavignesh.authserver.adminportal.externalsource.ExternalSourceService;
import com.chellavignesh.authserver.adminportal.externalsource.entity.ExternalSource;
import com.chellavignesh.authserver.adminportal.externalsource.exception.InvalidBrandingException;
import com.chellavignesh.authserver.adminportal.forgotcredentials.ForgotCredentialsService;
import com.chellavignesh.authserver.adminportal.forgotcredentials.exceptions.FailedToSendEmailException;
import com.chellavignesh.authserver.adminportal.forgotusername.UsernameLookupFieldService;
import com.chellavignesh.authserver.adminportal.forgotusername.entity.UsernameLookupCriteria;
import com.chellavignesh.authserver.adminportal.forgotusername.exception.*;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.chellavignesh.authserver.adminportal.user.exception.MobileAccountAccessBadRequestException;
import com.chellavignesh.authserver.adminportal.user.exception.UserNotFoundException;
import com.chellavignesh.authserver.adminportal.util.UUIDUtils;
import com.chellavignesh.authserver.cms.BrandUrlMappingService;
import com.chellavignesh.authserver.cms.CmsService;
import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.session.CustomUserDetailsService;
import com.chellavignesh.authserver.session.NotificationEmail;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Controller
@RequestMapping(value = {"/forgot-username", "/mobile-forgot-username"})
public class ForgotUsernameController {

    private static final String applicationLoginEntryPointLink = "/services/login";

    @Value("${toggles.forgot.username.enable:false}")
    private boolean isForgotUsernameFeatureEnabled;

    private final ForgotCredentialsService forgotCredentialsService;
    private final CustomUserDetailsService customUserDetailsService;
    private final CmsService cmsService;
    private final UserService userService;
    private final BrandUrlMappingService brandUrlMappingService;
    private final UsernameLookupFieldService usernameLookupFieldService;
    private final ExternalSourceService externalSourceService;

    private final String FORGOT_USERNAME_FLOW = "forgot-username";
    private final String MOBILE_FORGOT_USERNAME_FLOW = "mobile-forgot-username";

    @Autowired
    public ForgotUsernameController(ForgotCredentialsService forgotCredentialsService, CustomUserDetailsService customUserDetailsService, CmsService cmsService, UserService userService, BrandUrlMappingService brandUrlMappingService, UsernameLookupFieldService usernameLookupFieldService, ExternalSourceService externalSourceService) {
        this.forgotCredentialsService = forgotCredentialsService;
        this.customUserDetailsService = customUserDetailsService;
        this.cmsService = cmsService;
        this.userService = userService;
        this.brandUrlMappingService = brandUrlMappingService;
        this.usernameLookupFieldService = usernameLookupFieldService;
        this.externalSourceService = externalSourceService;
    }

    @GetMapping("")
    public String getForgotUsernameHomeScreen(@RequestParam(value = "cid", required = false) String clientIdParam, @RequestParam(value = "eid", required = false) String hexExternalIdParam, Model model, HttpServletRequest request) throws InvalidUserSessionException, FailedToFetchForgotUsernameViewException {

        final var flow = getFlow(request);
        var session = request.getSession();
        if (session == null) {
            throw new InvalidUserSessionException("Invalid user session");
        }

        try {
            String clientId;
            String branding;

            if (Objects.equals(flow, MOBILE_FORGOT_USERNAME_FLOW)) {
                validateMobileForgotUsernameParameters(clientIdParam, hexExternalIdParam);
                clientId = clientIdParam;
                branding = getBranding(hexExternalIdParam);

                session.setAttribute(ApplicationConstants.CLIENT_ID, clientId);
                session.setAttribute(ApplicationConstants.BRANDING_INFO, branding);
                session.setAttribute(ApplicationConstants.CLIENT_ID_SHORT_PARAMETER, clientIdParam);
                session.setAttribute(ApplicationConstants.EXTERNAL_ID_SHORT_PARAMETER, hexExternalIdParam);
            } else {
                clientId = getClientIdFromRequestSession(request);
            }

            var cmsData = cmsService.getCmsInfoForRequest(request);

            ApplicationDetail applicationDetail = forgotCredentialsService.getApplicationDetailsByClientId(clientId).get();

            boolean allowForgotUsername = isForgotUsernameFeatureEnabled && applicationDetail.getAllowForgotUsername();

            if (!allowForgotUsername) {
                throw new InvalidUserSessionException("Forgot username flow disabled");
            }

            ArrayList<UsernameLookupCriteria> lookupCriteriaList = applicationDetail.getUsernameLookupCriteria();

            String[] lookupCriteria = new String[]{};

            Integer lookupPriority = (Integer) session.getAttribute(ApplicationConstants.USERNAME_LOOKUP_PRIORITY);

            if (lookupPriority == null || lookupPriority >= lookupCriteriaList.size()) {
                lookupPriority = 0;
                session.setAttribute(ApplicationConstants.USERNAME_LOOKUP_PRIORITY, lookupPriority);
            }

            model.addAttribute("contact", cmsData.getOrDefault("helpContentHTML", ""));
            model.addAttribute("cancelUrl", applicationLoginEntryPointLink);
            model.addAttribute("description", cmsData.getOrDefault("forgotUsernameContent2", ""));
            model.addAttribute("logo", cmsData.getOrDefault("loginLogoPath", ""));

            model.addAttribute("planName", cmsData.getOrDefault("planName", ""));
            model.addAttribute("planFaviconPath", cmsData.getOrDefault("planFaviconPath", ""));

            model.addAttribute("metaTitle", "Forgot Username - ".concat(cmsData.getOrDefault("forgotUsernameNavLink1", "")));

            model.addAttribute("progressSidebarLinks", List.of(cmsData.getOrDefault("progressBarLinkHTML1", ""), cmsData.getOrDefault("progressBarLinkHTML2", ""), cmsData.getOrDefault("progressBarLinkHTML3", ""), cmsData.getOrDefault("progressBarLinkHTML4", ""), cmsData.getOrDefault("progressBarLinkHTML5", ""), cmsData.getOrDefault("progressBarLinkHTML6", "")));

            model.addAttribute("progressSidebarSteps", List.of(cmsData.getOrDefault("forgotUsernameNavLink1", "")));

            model.addAttribute("subtitle", cmsData.getOrDefault("forgotUsernameContent", ""));
            model.addAttribute("title", cmsData.getOrDefault("forgotUsernameTitle", ""));
            model.addAttribute("footerLinks", cmsData.getOrDefault("footerLinks", ""));
            model.addAttribute("footerHTML", cmsData.getOrDefault("footerHTML", ""));

            model.addAttribute("planPrimaryColor", cmsData.getOrDefault("planPrimaryColor", ""));
            model.addAttribute("planSecondaryColor", cmsData.getOrDefault("planSecondaryColor", ""));
            model.addAttribute("planTertiaryColor", cmsData.getOrDefault("planTertiaryColor", ""));
            model.addAttribute("planPrimaryTextColor", cmsData.getOrDefault("planPrimaryTextColor", ""));
            model.addAttribute("planSecondaryTextColor", cmsData.getOrDefault("planSecondaryTextColor", ""));
            model.addAttribute("planTertiaryTextColor", cmsData.getOrDefault("planTertiaryTextColor", ""));
            model.addAttribute("planPublicWebLink", cmsData.getOrDefault("planPublicWebLink", ""));
            model.addAttribute("usernameVerificationStep1", cmsData.getOrDefault("usernameVerificationStep1", "ACCOUNT VERIFICATION"));

            model.addAttribute("backUrlPath", applicationLoginEntryPointLink);

            if (session.getAttribute("noUserFoundError") != null) {
                model.addAttribute("error", session.getAttribute("noUserFoundError").toString());
                session.removeAttribute("noUserFoundError");
                model.addAttribute("backUrlPath", "@{/forgot-username}");
            }

            if (session.getAttribute("failedToSendEmail") != null) {
                model.addAttribute("error", session.getAttribute("failedToSendEmail").toString());
                session.removeAttribute("failedToSendEmail");
            }

            if (session.getAttribute("success") != null) {
                model.addAttribute("success", session.getAttribute("success"));
                session.removeAttribute("success");
            }

            if (session.getAttribute("error") != null) {
                model.addAttribute("error", session.getAttribute("error"));
                session.removeAttribute("error");
            }

            if (!lookupCriteriaList.isEmpty()) {
                if (lookupPriority > 0 && Objects.equals(model.getAttribute("backUrlPath"), applicationLoginEntryPointLink)) {
                    lookupPriority = 0;
                    session.setAttribute(ApplicationConstants.USERNAME_LOOKUP_PRIORITY, lookupPriority);
                }

                lookupCriteria = lookupCriteriaList.get(lookupPriority).getLookupCriteria();
            }

            List<UsernameLookupField> lookupFields = this.criteriaAsUsernameLookupFieldObjects(lookupCriteria);

            model.addAttribute("usernameLookupFields", lookupFields);
            session.setAttribute("forgotUsernameLookupPriority", lookupPriority);

            log.info("Looking up forgotten username based on fields at priority: {}", lookupPriority);

            return "pages/%s/identify-yourself".formatted(flow);

        } catch (Exception ex) {
            throw new FailedToFetchForgotUsernameViewException("Error while fetching Forgot username page", ex);
        }
    }

    @PostMapping("/identify-yourself")
    public String postForgotUsernameIdentifyYourself(Model model, HttpServletRequest request, @RequestParam Map<String, String> usernameLookupParams) throws InvalidUserSessionException, FailedToRecoverUsernameException {

        final var flow = getFlow(request);
        var sessionId = request.getSession();
        String branding = (String) request.getSession().getAttribute(ApplicationConstants.BRANDING_INFO);
        var redirectRootUrl = brandUrlMappingService.getUrlByBrand(branding);

        if (sessionId == null || StringUtils.isEmpty(branding)) {
            throw new InvalidUserSessionException("Invalid user session");
        }

        try {
            String clientId = getClientIdFromRequestSession(request);

            ApplicationDetail applicationDetail = forgotCredentialsService.getApplicationDetailsByClientId(clientId).get();

            ArrayList<UsernameLookupCriteria> lookupCriteriaList = applicationDetail.getUsernameLookupCriteria();

            Integer lookupPriority = (Integer) request.getSession().getAttribute(ApplicationConstants.USERNAME_LOOKUP_PRIORITY);

            validateUserLookupParameters(usernameLookupParams, lookupCriteriaList.get(lookupPriority));

            var cmsData = cmsService.getCmsInfoForRequest(request);
            UserDetails userDetails;

            try {
                String username = this.customUserDetailsService.loadUsernameByBrand(branding, usernameLookupParams);

                userDetails = userService.getByUsernameAndBranding(username, branding).orElseThrow(() -> new UserNotFoundException("User not found for username: %s".formatted(username)));

                var dateFormatter = DateTimeFormatter.ofPattern("dd-MMM-yy");
                var body = "<root><username><![CDATA[" + username + "]]></username></root>";

                var metadata = new NotificationEmail.Metadata(branding, "forgotten_username", null, "ui", "N", LocalDate.now().format(dateFormatter), "FU");

                var notificationEmail = new NotificationEmail(null, null, userDetails.email(), null, null, null, body, "HIGH", metadata);

                this.forgotCredentialsService.emailUsername(notificationEmail);

            } catch (UserNotFoundException ex) {
                removeUsernameLookupPriority(request);
                setNoUserFoundError(request, cmsData);
                log.error("Username was not found in idp.", ex);
                return getRedirectUrl(request, flow, redirectRootUrl, false);

            } catch (UniqueUsernameNotfoundException ex) {
                request.getSession().setAttribute(ApplicationConstants.USERNAME_LOOKUP_PRIORITY, lookupPriority + 1);
                setNoUserFoundError(request, cmsData);
                log.error("Failed to find unique username using priority %d fields. Username lookup will continue with next priority fields.".formatted(lookupPriority), ex);
                return getRedirectUrl(request, flow, redirectRootUrl, false);
            } catch (ConflictUsernameLookupSearchException ex) {
                request.getSession().setAttribute(ApplicationConstants.USERNAME_LOOKUP_PRIORITY, lookupPriority + 1);
                setNoUserFoundError(request, cmsData);
                log.error("Username lookup resulted in more than one record. Priority %d fields. Username lookup will continue with next priority fields. {}", lookupPriority, ex);
                return getRedirectUrl(request, flow, redirectRootUrl, false);

            } catch (FailedToSendEmailException ex) {
                removeUsernameLookupPriority(request);
                request.getSession().setAttribute("error", cmsData.getOrDefault("failedToSendEmail", ""));
                log.error("Error while emailing forgotten username.", ex);
                return getRedirectUrl(request, flow, redirectRootUrl, false);

            } catch (InvalidBrandingException ex) {
                removeUsernameLookupPriority(request);
                setNoUserFoundError(request, cmsData);
                log.error("Brand %s does not have an external source. {}", branding, ex);
                return getRedirectUrl(request, flow, redirectRootUrl, false);
            }

            removeUsernameLookupPriority(request);
            request.getSession().setAttribute("success", "Username will be sent to the email address");
            return getRedirectUrl(request, flow, redirectRootUrl, true);

        } catch (Exception ex) {
            removeUsernameLookupPriority(request);
            log.error("Error while recovering forgotten username.", ex);
            throw new FailedToRecoverUsernameException("Error while recovering forgotten username.", ex);
        }
    }

    private void removeUsernameLookupPriority(HttpServletRequest request) {
        request.getSession().removeAttribute(ApplicationConstants.USERNAME_LOOKUP_PRIORITY);
    }

    private void setNoUserFoundError(HttpServletRequest request, Map<String, String> cmsData) {
        request.getSession().setAttribute("noUserFoundError", cmsData.getOrDefault("noUserFoundError", null));
    }

    private String getRedirectUrl(HttpServletRequest request, String flow, URI redirectRootUrl, boolean redirectToLogin) {
        if (Objects.equals(flow, MOBILE_FORGOT_USERNAME_FLOW)) {
            String cid = (String) request.getSession().getAttribute(ApplicationConstants.CLIENT_ID_SHORT_PARAMETER);
            String eid = (String) request.getSession().getAttribute(ApplicationConstants.EXTERNAL_ID_SHORT_PARAMETER);

            String param = ApplicationConstants.CLIENT_ID_SHORT_PARAMETER + "=" + cid + "&" + ApplicationConstants.EXTERNAL_ID_SHORT_PARAMETER + "=" + eid;

            return "redirect:%s/%s?%s".formatted(redirectRootUrl, flow, param);
        } else {
            if (redirectToLogin) {
                request.getSession().removeAttribute(ApplicationConstants.AUTH_SESSION_ID);
                return "redirect:%s/login".formatted(redirectRootUrl);
            }
            return "redirect:%s/%s".formatted(redirectRootUrl, flow);
        }
    }

    private void validateUserLookupParameters(Map<String, String> usernameLookupParams, UsernameLookupCriteria usernameLookupCriteria) throws BadRequestException {

        for (int i = 0; i < usernameLookupCriteria.getLookupCriteria().length; i++) {

            if (usernameLookupParams.get(usernameLookupCriteria.getLookupCriteria()[i]) == null) {

                log.error("Required parameters missing in request: {}", usernameLookupCriteria.getLookupCriteria()[i]);
                throw new BadRequestException("Required parameters missing");
            }
        }
    }

    private String getClientIdFromRequestSession(HttpServletRequest request) {
        if (request.getSession() == null) {
            return null;
        }
        return (String) request.getSession().getAttribute(ApplicationConstants.CLIENT_ID);
    }

    private List<UsernameLookupField> criteriaAsUsernameLookupFieldObjects(String[] criteria) {
        List<UsernameLookupField> fieldList = new ArrayList<>();

        if (ArrayUtils.isEmpty(criteria)) {
            return fieldList;
        }

        var lookupFieldMap = usernameLookupFieldService.getAllAsMap();

        for (String fieldName : criteria) {
            fieldList.add(new UsernameLookupField(fieldName, lookupFieldMap.get(fieldName).getDescription()));
        }
        return fieldList;
    }

    private String getFlow(HttpServletRequest request) {
        if (request.getRequestURI().contains("mobile-forgot-username")) {
            return MOBILE_FORGOT_USERNAME_FLOW;
        } else {
            return FORGOT_USERNAME_FLOW;
        }
    }

    private void validateMobileForgotUsernameParameters(String clientId, String hexExternalId) throws MobileAccountAccessBadRequestException {

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

    @ExceptionHandler({AppNotFoundException.class, FailedToFetchForgotUsernameViewException.class, UsernameNotFoundException.class})
    public String handleAppFlowExceptions() {
        return "pages/404";
    }


}

