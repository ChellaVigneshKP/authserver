package com.chellavignesh.authserver.adminportal.controller;

import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.application.exception.AppNotFoundException;
import com.chellavignesh.authserver.adminportal.externalsource.exception.InvalidBrandingException;
import com.chellavignesh.authserver.adminportal.forgotcredentials.ForgotCredentialsService;
import com.chellavignesh.authserver.adminportal.forgotusername.exception.InvalidUserSessionException;
import com.chellavignesh.authserver.adminportal.forgotusername.exception.InvalidUserSessionParametersException;
import com.chellavignesh.authserver.adminportal.forgotusername.exception.InvalidUserSessionSecurityException;
import com.chellavignesh.authserver.adminportal.manageprofile.ManageProfileService;
import com.chellavignesh.authserver.adminportal.manageprofile.exception.AuthorizationMissingException;
import com.chellavignesh.authserver.adminportal.manageprofile.exception.TokenExpiredException;
import com.chellavignesh.authserver.adminportal.metadata.MetadataService;
import com.chellavignesh.authserver.adminportal.metadata.dto.OutgoingMetadataDto;
import com.chellavignesh.authserver.adminportal.toggle.AccountSyncType;
import com.chellavignesh.authserver.adminportal.user.OnPremAccountServiceClient;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.dto.ChangePasswordDto;
import com.chellavignesh.authserver.adminportal.user.dto.NotifiableUpdateUserPasswordDto;
import com.chellavignesh.authserver.adminportal.user.dto.UpdateIntent;
import com.chellavignesh.authserver.adminportal.user.dto.validator.ChangePasswordDtoValidator;
import com.chellavignesh.authserver.adminportal.user.entity.UserAuthDetails;
import com.chellavignesh.authserver.adminportal.user.exception.AccountSyncException;
import com.chellavignesh.authserver.adminportal.user.exception.ChangeUserPasswordBadRequestException;
import com.chellavignesh.authserver.adminportal.user.exception.UserNotFoundException;
import com.chellavignesh.authserver.adminportal.user.exception.UserUpdateFailedException;
import com.chellavignesh.authserver.cms.BrandUrlMappingService;
import com.chellavignesh.authserver.cms.CmsService;
import com.chellavignesh.authserver.cms.exception.CmsFileNotFoundException;
import com.chellavignesh.authserver.cms.exception.CmsProcessingException;
import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.security.PasswordValidatorService;
import com.chellavignesh.authserver.security.exception.PasswordValidationException;
import com.chellavignesh.authserver.security.exception.RequestDatetimeInvalidException;
import com.chellavignesh.authserver.session.PasswordEncoderFactory;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.session.sso.exception.FingerprintFailedException;
import com.chellavignesh.authserver.session.sso.exception.InactiveAuthSessionException;
import com.chellavignesh.authserver.token.SignatureService;
import com.chellavignesh.authserver.token.exception.SignatureVerificationFailedException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.chellavignesh.authserver.adminportal.manageprofile.RedirectErrorCode.*;
import static com.chellavignesh.authserver.adminportal.user.dto.validator.ChangePasswordDtoValidator.INVALID_CURRENT_PASSWORD;
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
    public String changeUserPassword(@RequestParam String p, Model model, HttpServletRequest request, @CookieValue(SSO_COOKIE_NAME) String idpSessionCookieVal) throws CmsProcessingException, CmsFileNotFoundException, InvalidUserSessionSecurityException, InvalidUserSessionException, ChangeUserPasswordBadRequestException {

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

    @PostMapping("/user/change-password")
    public String updateUserPassword(HttpServletRequest request, @Valid @ModelAttribute ChangePasswordDto changePasswordDto, BindingResult bindingResult, @RequestParam(value = "Authorization", required = false) String authHeader, @RequestParam(value = "x-signature", required = false) String signature, @RequestParam(value = "x-request-datetime", required = false) String requestDatetime, @RequestParam(value = "x-request-id", required = false) String requestId) throws InvalidUserSessionException, AuthorizationMissingException, AppNotFoundException {

        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        request.getSession().removeAttribute(ERROR_SESSION_ATTRIBUTE);

        String surl = (String) request.getSession().getAttribute(SOURCE_RELATIVE_PATH_PARAMETER);
        String mt = (String) request.getSession().getAttribute(METADATA_PARAMETER);

        if (authHeader == null) {
            throw new AuthorizationMissingException("Authorization missing from the request parameter");
        }

        var token = manageProfileService.getTokenFromAuthHeader(authHeader);
        var authSession = manageProfileService.validateSessionFromToken(token);

        if (bindingResult.hasErrors()) {
            var fieldError = bindingResult.getFieldError();
            String message = fieldError != null ? fieldError.getDefaultMessage() : "Invalid input";

            return this.redirectToChangePasswordPageWithError(request, surl, mt, message, authSession.getBranding());
        }

        try {
            manageProfileService.validateToken(token, authSession);
        } catch (TokenExpiredException e) {
            log.error("Token expired/inactive.", e);
            return "redirect:%s".formatted(manageProfileService.getRedirect(surl, ACCESS_TOKEN_EXPIRED, authSession));
        }

        try {
            if (signature == null) {
                throw new SignatureVerificationFailedException("Missing signature.");
            }

            ObjectMapper mapper = new ObjectMapper();
            var body = mapper.writeValueAsString(changePasswordDto);

            var verifiedSignature = signatureService.verifySignature(authHeader, signature, body.getBytes(StandardCharsets.UTF_8));

            if (!verifiedSignature) {
                log.error("Signatures did not match.");
                return "redirect:%s".formatted(manageProfileService.getRedirect(surl, INVALID_SIGNATURE, authSession));
            }

        } catch (SignatureVerificationFailedException e) {
            log.error("Signature verification failed.", e);
            return "redirect:%s".formatted(manageProfileService.getRedirect(surl, INVALID_SIGNATURE, authSession));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        try {
            manageProfileService.validateSessionFingerprint(authSession, request, requestDatetime);
            manageProfileService.validateRequestDatetime(request, authSession.getApplicationId(), requestDatetime);
        } catch (FingerprintFailedException | InactiveAuthSessionException e) {
            log.error("Fingerprint validation failed.", e);
            return "redirect:%s".formatted(manageProfileService.getRedirect(surl, INVALID_FINGERPRINT, authSession));
        } catch (RequestDatetimeInvalidException e) {
            log.error("Request datetime invalid.", e);
            return "redirect:%s".formatted(manageProfileService.getRedirect(surl, EXPIRED_DATETIME, authSession));
        }

        try {
            UserAuthDetails userAuthDetails = userService.getUserAuthDetailsByUsernameAndExternalSourceCode(authSession.getSubjectId(), authSession.getBranding()).orElseThrow(() -> new UserNotFoundException("User not found."));

            // Validate current password
            if (!passwordEncoderFactory.matches(changePasswordDto.getCurrentPassword(), userAuthDetails.getPassword(), userAuthDetails.getVersion())) {
                return this.redirectToChangePasswordPageWithError(request, surl, mt, INVALID_CURRENT_PASSWORD, authSession.getBranding());
            }

            // Decode Base64 passwords
            if (changePasswordDto.getNewPassword() != null) {
                changePasswordDto.setNewPassword(new String(Base64.getDecoder().decode(changePasswordDto.getNewPassword())));
            }

            if (changePasswordDto.getCurrentPassword() != null) {
                changePasswordDto.setCurrentPassword(new String(Base64.getDecoder().decode(changePasswordDto.getCurrentPassword())));
            }

            if (changePasswordDto.getConfirmPassword() != null) {
                changePasswordDto.setConfirmPassword(new String(Base64.getDecoder().decode(changePasswordDto.getConfirmPassword())));
            }

            final var dto = new NotifiableUpdateUserPasswordDto();
            dto.setPassword(changePasswordDto.getNewPassword());
            dto.setBranding(authSession.getBranding());

            passwordValidatorService.validatePassword(dto.getPassword(), userAuthDetails.getRowGuid());

            var userDetails = userService.getByUsernameAndBranding(authSession.getSubjectId(), authSession.getBranding()).orElseThrow(() -> new UserNotFoundException("User not found"));

            String clientId = getClientIdFromRequestSession(request);
            Application application = forgotPasswordService.checkClientId(clientId).get();

            boolean isEntity = application.getCmsContext() != null && application.getCmsContext().equals("entity");

            final var optionalMetadata = metadataService.tryReadFromSessionAsType(request, OutgoingMetadataDto.class);

            if (optionalMetadata.isEmpty()) {
                return this.redirectToChangePasswordPageWithError(request, surl, mt, "Something went wrong. Please try again.", authSession.getBranding());
            }

            final var metadata = optionalMetadata.get();


            if ((!isEntity || userDetails.credSyncFlag()) && AccountSyncType.SYNC == accountSyncType) {

                var response = onPremAccountService.syncPassword(dto.getPassword(), metadata, userDetails, authSession.getBranding(), requestId);

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return this.redirectToChangePasswordPageWithError(request, surl, mt, "Something went wrong. Please try again.", authSession.getBranding());
                }

            } else if (Boolean.TRUE.equals(userDetails.credSyncFlag()) && AccountSyncType.ASYNC == accountSyncType) {

                dto.setIntent(UpdateIntent.IDP);
                dto.setMetadata(metadata);

                Map<String, String> sourceHeaders = Map.of(ApplicationConstants.REQUEST_ID_HEADER, requestId, ApplicationConstants.AUTHORIZATION_HEADER, authHeader, ApplicationConstants.REQUEST_DATETIME_HEADER, requestDatetime);

                userService.updatePasswordAndNotify(dto, userDetails.rowGuid(), false, sourceHeaders);

            } else {
                userService.updatePassword(dto, userDetails.rowGuid());
            }

        } catch (UserNotFoundException | UserUpdateFailedException | InvalidBrandingException | AccountSyncException e) {
            return this.redirectToChangePasswordPageWithError(request, surl, mt, "Something went wrong. Please try again.", authSession.getBranding());
        } catch (PasswordValidationException e) {
            return this.redirectToChangePasswordPageWithError(request, surl, mt, e.getMessage(), authSession.getBranding());
        }

        return "redirect:%s".formatted(manageProfileService.getRedirect(surl, authSession));
    }

    String redirectToChangePasswordPageWithError(HttpServletRequest request, String surl, String metadata, String errorMessage, String brand) {
        request.getSession().setAttribute(ERROR_SESSION_ATTRIBUTE, errorMessage);

        var newRequestDateTime = Instant.now().atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        String p = "surl=%s&mt=%s&request-datetime=%s".formatted(surl, metadata, newRequestDateTime);

        var redirectRootUrl = brandUrlMappingService.getUrlByBrand(brand);

        return "redirect:%s/user/change-password?p=%s".formatted(redirectRootUrl, Base64.getEncoder().encodeToString(p.getBytes()));
    }

    String getClientIdFromRequestSession(HttpServletRequest request) {
        if (request.getSession() == null) {
            return null;
        }
        return (String) request.getSession().getAttribute(ApplicationConstants.CLIENT_ID);
    }

    @ExceptionHandler({InvalidUserSessionSecurityException.class, MissingRequestCookieException.class})
    public ResponseEntity<?> handleSecurityStateException(Exception e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid user session security configuration");
    }

    @ExceptionHandler(InvalidUserSessionParametersException.class)
    public ResponseEntity<?> handleInvalidUserSessionParametersException(InvalidUserSessionParametersException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(ChangeUserPasswordBadRequestException.class)
    public ResponseEntity<?> handleChangeUserPasswordBadRequestException(ChangeUserPasswordBadRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(AuthorizationMissingException.class)
    public String handleAuthorizationMissing(AuthorizationMissingException e) {
        return "redirect:404";
    }

    @ExceptionHandler(InvalidUserSessionException.class)
    public String handleInvalidUserSession(InvalidUserSessionException e) {
        return "redirect:404";
    }
}

