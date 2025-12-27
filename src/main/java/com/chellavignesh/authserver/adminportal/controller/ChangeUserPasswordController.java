package com.chellavignesh.authserver.adminportal.controller;

import com.chellavignesh.authserver.adminportal.forgotcredentials.ForgotCredentialsService;
import com.chellavignesh.authserver.adminportal.forgotusername.exception.InvalidUserSessionException;
import com.chellavignesh.authserver.adminportal.forgotusername.exception.InvalidUserSessionParametersException;
import com.chellavignesh.authserver.adminportal.forgotusername.exception.InvalidUserSessionSecurityException;
import com.chellavignesh.authserver.adminportal.manageprofile.ManageProfileService;
import com.chellavignesh.authserver.adminportal.metadata.MetadataService;
import com.chellavignesh.authserver.adminportal.toggle.AccountSyncType;
import com.chellavignesh.authserver.adminportal.user.OnPremAccountServiceClient;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.dto.validator.ChangePasswordDtoValidator;
import com.chellavignesh.authserver.adminportal.user.exception.ChangeUserPasswordBadRequestException;
import com.chellavignesh.authserver.cms.BrandUrlMappingService;
import com.chellavignesh.authserver.cms.CmsService;
import com.chellavignesh.authserver.cms.exception.CmsFileNotFoundException;
import com.chellavignesh.authserver.cms.exception.CmsProcessingException;
import com.chellavignesh.authserver.security.PasswordValidatorService;
import com.chellavignesh.authserver.security.exception.RequestDatetimeInvalidException;
import com.chellavignesh.authserver.session.PasswordEncoderFactory;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.session.sso.exception.FingerprintFailedException;
import com.chellavignesh.authserver.session.sso.exception.InactiveAuthSessionException;
import com.chellavignesh.authserver.token.SignatureService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

import static com.chellavignesh.authserver.adminportal.manageprofile.RedirectErrorCode.ACCESS_TOKEN_EXPIRED;
import static com.chellavignesh.authserver.adminportal.manageprofile.RedirectErrorCode.OPERATION_CANCELLED;
import static com.chellavignesh.authserver.config.ApplicationConstants.*;

@Slf4j
@Controller
public class ChangeUserPasswordController {

    private static final String ERROR_SESSION_ATTRIBUTE = "changePasswordError";

    private final AccountSyncType accountSyncType;
    private final CmsService cmsService;
    private final UserService userService;
    private final ManageProfileService manageProfileService;
    private final SignatureService signatureService;
    private final PasswordValidatorService passwordValidatorService;
    private final ChangePasswordDtoValidator changePasswordDtoValidator;
    private final PasswordEncoderFactory passwordEncoderFactory;
    private final OnPremAccountServiceClient onPremAccountService;
    private final MetadataService metadataService;
    private final BrandUrlMappingService brandUrlMappingService;
    private final ForgotCredentialsService forgotPasswordService;

    public ChangeUserPasswordController(CmsService cmsService, UserService userService, ManageProfileService manageProfileService, SignatureService signatureService, ChangePasswordDtoValidator changePasswordDtoValidator, @Value("${toggles.account.sync.type:sync}") AccountSyncType accountSyncType, PasswordValidatorService passwordValidatorService, PasswordEncoderFactory passwordEncoderFactory, OnPremAccountServiceClient onPremAccountService, MetadataService metadataService, BrandUrlMappingService brandUrlMappingService, ForgotCredentialsService forgotPasswordService) {
        this.cmsService = cmsService;
        this.userService = userService;
        this.manageProfileService = manageProfileService;
        this.signatureService = signatureService;
        this.passwordValidatorService = passwordValidatorService;
        this.changePasswordDtoValidator = changePasswordDtoValidator;
        this.passwordEncoderFactory = passwordEncoderFactory;
        this.accountSyncType = accountSyncType;
        this.onPremAccountService = onPremAccountService;
        this.metadataService = metadataService;
        this.brandUrlMappingService = brandUrlMappingService;
        this.forgotPasswordService = forgotPasswordService;
    }

    @InitBinder(value = "changePasswordDto")
    public void initBinder(WebDataBinder binder) {
        binder.addValidators(changePasswordDtoValidator);
    }

    @GetMapping("/user/change-password")
    public String changeUserPassword(@RequestParam String p, Model model, HttpServletRequest request, @CookieValue(SSO_COOKIE_NAME) String idpSessionCookieVal) throws CmsProcessingException, CmsFileNotFoundException, InvalidUserSessionSecurityException, InvalidUserSessionParametersException, InvalidUserSessionException, ChangeUserPasswordBadRequestException {

        Map<String, String> parameters = manageProfileService.decodeRequestParameter(p);

        if (!parameters.containsKey(SOURCE_RELATIVE_PATH_PARAMETER) || metadataService.isMetadataParameterInvalid(parameters, "Unable to validate request") || !parameters.containsKey(REQUEST_DATETIME_HEADER)) {

            throw new ChangeUserPasswordBadRequestException("Missing required parameter");
        }

        AuthSession session;
        try {
            session = manageProfileService.validateSessionInCookie(request, idpSessionCookieVal, parameters.get(REQUEST_DATETIME_HEADER));

            manageProfileService.validateRequestDatetime(request, session.getApplicationId(), parameters.get(REQUEST_DATETIME_HEADER));

        } catch (FingerprintFailedException | RequestDatetimeInvalidException | InactiveAuthSessionException e) {

            log.error("Error while validating the request.", e);
            throw new InvalidUserSessionException("Error while validating the request");
        }

        userService.getByUsernameAndBranding(session.getSubjectId(), session.getBranding()).orElseThrow(() -> new InvalidUserSessionException("User not found"));

        String surl = parameters.get(SOURCE_RELATIVE_PATH_PARAMETER);
        String mt = parameters.get(METADATA_PARAMETER);

        request.getSession().setAttribute(SOURCE_RELATIVE_PATH_PARAMETER, surl);
        request.getSession().setAttribute(METADATA_PARAMETER, mt);

        Object existingAuthSessionId = request.getSession().getAttribute(AUTH_SESSION_ID);

        String currentSessionId = existingAuthSessionId != null ? existingAuthSessionId.toString() : null;

        if (currentSessionId == null || !currentSessionId.equals(session.getSessionId().toString())) {

            request.getSession().setAttribute(AUTH_SESSION_ID, session.getSessionId());
        }

        var cmsData = cmsService.getCmsInfoFromSession(session);

        model.addAttribute("planName", cmsData.getOrDefault("planName", ""));
        model.addAttribute("planFaviconPath", cmsData.getOrDefault("planFaviconPath", ""));
        model.addAttribute("logo", cmsData.getOrDefault("loginLogoPath", ""));
        model.addAttribute("metaTitle", "");

        model.addAttribute("progressSidebarLinks", List.of(cmsData.getOrDefault("progressBarLinkHTML1", ""), cmsData.getOrDefault("progressBarLinkHTML2", ""), cmsData.getOrDefault("progressBarLinkHTML3", ""), cmsData.getOrDefault("progressBarLinkHTML4", ""), cmsData.getOrDefault("progressBarLinkHTML5", ""), cmsData.getOrDefault("progressBarLinkHTML6", "")));

        model.addAttribute("progressSidebarSteps", List.of(cmsData.getOrDefault("passwordChangeNavLink1", "")));

        model.addAttribute("contact", cmsData.getOrDefault("helpContentHTML", ""));
        model.addAttribute("title", cmsData.getOrDefault("passwordChangeTitle", ""));
        model.addAttribute("subtitle", cmsData.getOrDefault("passwordChangeSubTitle", ""));

        model.addAttribute("passwordChangeDescription", "Enter the information below to change your password");
        model.addAttribute("passwordChangeEnterCurrentPassword", "Enter current password");
        model.addAttribute("passwordChangeCreateNewPassword", "Create new password");
        model.addAttribute("passwordChangeRequiredText", "All fields are required unless indicated as optional.");

        model.addAttribute("helpTexts", List.of("Must be at least 8 characters long", "Must include at least 3 of the following:\n" + "Lower characters, upper characters, special characters, or numbers", "Cannot include first name, last name, username or email"));

        model.addAttribute("footerLinks", cmsData.getOrDefault("footerLinks", ""));
        model.addAttribute("footerHTML", cmsData.getOrDefault("footerHTML", ""));

        if (request.getSession().getAttribute(ERROR_SESSION_ATTRIBUTE) != null) {

            model.addAttribute("error", request.getSession().getAttribute(ERROR_SESSION_ATTRIBUTE).toString());

            request.getSession().removeAttribute(ERROR_SESSION_ATTRIBUTE);
        }

        model.addAttribute("backUrlPath", manageProfileService.getRedirect(surl, OPERATION_CANCELLED, session));

        model.addAttribute("accessTokenErrorPath", manageProfileService.getRedirect(surl, ACCESS_TOKEN_EXPIRED, session));

        return "pages/user/change-password";
    }
}

