package com.chellavignesh.authserver.adminportal.controller;

import com.chellavignesh.authserver.adminportal.externalsource.ExternalSourceService;
import com.chellavignesh.authserver.adminportal.forgotusername.exception.ChangeUserProfileBadRequestException;
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
import com.chellavignesh.authserver.adminportal.user.dto.ChangeProfileDto;
import com.chellavignesh.authserver.adminportal.user.dto.validator.ChangeProfileDtoValidator;
import com.chellavignesh.authserver.adminportal.user.dto.validator.ChangeProfileViewDtoValidator;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.chellavignesh.authserver.adminportal.user.exception.AccountSyncException;
import com.chellavignesh.authserver.adminportal.user.exception.UserNotFoundException;
import com.chellavignesh.authserver.adminportal.user.exception.UserUpdateFailedException;
import com.chellavignesh.authserver.adminportal.util.HttpVerb;
import com.chellavignesh.authserver.adminportal.util.PhoneNumberUtil;
import com.chellavignesh.authserver.cms.BrandUrlMappingService;
import com.chellavignesh.authserver.cms.CmsService;
import com.chellavignesh.authserver.cms.exception.CmsFileNotFoundException;
import com.chellavignesh.authserver.cms.exception.CmsProcessingException;
import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.security.exception.RequestDatetimeInvalidException;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.session.sso.exception.FingerprintFailedException;
import com.chellavignesh.authserver.session.sso.exception.InactiveAuthSessionException;
import com.chellavignesh.authserver.token.SignatureService;
import com.chellavignesh.authserver.token.exception.SignatureVerificationFailedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.chellavignesh.authserver.adminportal.manageprofile.RedirectErrorCode.*;
import static com.chellavignesh.authserver.config.ApplicationConstants.*;

@Controller
@Slf4j
public class ChangeUserProfileController {

    private final AccountSyncType accountSyncType;
    private final CmsService cmsService;
    private final ManageProfileService manageProfileService;
    private final UserService userService;
    private final SignatureService signatureService;
    private final ChangeProfileViewDtoValidator changeProfileViewDtoValidator;
    private final ChangeProfileDtoValidator changeProfileDtoValidator;
    private final OnPremAccountServiceClient onPremAccountService;
    private final BrandUrlMappingService brandUrlMappingService;
    private final MetadataService metadataService;
    private final ExternalSourceService externalSourceService;

    @Autowired
    public ChangeUserProfileController(CmsService cmsService, ManageProfileService manageProfileService, UserService userService, SignatureService signatureService, ChangeProfileViewDtoValidator changeProfileViewDtoValidator, ChangeProfileDtoValidator changeProfileDtoValidator, OnPremAccountServiceClient onPremAccountService, MetadataService metadataService, BrandUrlMappingService brandUrlMappingService, @Value("${toggles.account.sync.type:sync}") AccountSyncType accountSyncType, ExternalSourceService externalSourceService) {
        this.cmsService = cmsService;
        this.manageProfileService = manageProfileService;
        this.userService = userService;
        this.signatureService = signatureService;
        this.changeProfileViewDtoValidator = changeProfileViewDtoValidator;
        this.changeProfileDtoValidator = changeProfileDtoValidator;
        this.onPremAccountService = onPremAccountService;
        this.metadataService = metadataService;
        this.accountSyncType = accountSyncType;
        this.brandUrlMappingService = brandUrlMappingService;
        this.externalSourceService = externalSourceService;
    }

    @InitBinder(value = "changeProfileDto")
    public void initBinder(WebDataBinder binder) {
        binder.addValidators(changeProfileDtoValidator);
    }

    @InitBinder(value = "changeProfileViewDto")
    public void initViewBinder(WebDataBinder binder) {
        binder.addValidators(changeProfileViewDtoValidator);
    }

    @GetMapping(value = "/user/change-profile", params = {"error"})
    public String changeUserProfileError() {
        return "pages/user/change-profile";
    }

    @GetMapping("/user/change-profile")
    public String changeUserProfile(@RequestParam String p, Model model, HttpServletRequest request, @CookieValue(SSO_COOKIE_NAME) String idpSessionCookieVal) throws CmsProcessingException, CmsFileNotFoundException, InvalidUserSessionSecurityException, InvalidUserSessionException, ChangeUserProfileBadRequestException {

        Map<String, String> parameters = manageProfileService.decodeRequestParameter(p);

        if (!parameters.containsKey(SOURCE_RELATIVE_PATH_PARAMETER) || metadataService.isMetadataParameterInvalid(parameters, "Unable to validate request") || !parameters.containsKey(REQUEST_DATETIME_HEADER)) {
            throw new ChangeUserProfileBadRequestException("Missing required parameter");
        }

        AuthSession session;
        try {
            session = manageProfileService.validateSessionInCookie(request, idpSessionCookieVal, parameters.get(REQUEST_DATETIME_HEADER));

            manageProfileService.validateRequestDatetime(request, session.getApplicationId(), parameters.get(REQUEST_DATETIME_HEADER));

        } catch (FingerprintFailedException | RequestDatetimeInvalidException | InactiveAuthSessionException e) {
            log.error("Error while validating the request.", e);
            throw new InvalidUserSessionException("Error while validating the request");
        }

        ChangeProfileDto changeProfileDto = (ChangeProfileDto) request.getSession().getAttribute("changeProfileDto");

        if (changeProfileDto != null) {
            request.getSession().removeAttribute("changeProfileDto");
        } else {
            UserDetails userDetails = userService.getByUsernameAndBranding(session.getSubjectId(), session.getBranding()).orElseThrow(() -> new InvalidUserSessionException("User not found"));

            changeProfileDto = new ChangeProfileDto();
            changeProfileDto.setFirstName(userDetails.firstName());
            changeProfileDto.setLastName(userDetails.lastName());
            changeProfileDto.setEmail(userDetails.email());
            changeProfileDto.setConfirmEmail(userDetails.email());
            changeProfileDto.setPhoneNumber(PhoneNumberUtil.transformE164ToPhoneNumber(userDetails.phoneNumber()));
            changeProfileDto.setSecondaryPhoneNumber(PhoneNumberUtil.transformE164ToPhoneNumber(userDetails.secondaryPhoneNumber()));
        }

        String url = parameters.get(SOURCE_RELATIVE_PATH_PARAMETER);
        String mt = parameters.get(METADATA_PARAMETER);

        request.getSession().setAttribute(SOURCE_RELATIVE_PATH_PARAMETER, url);
        request.getSession().setAttribute(METADATA_PARAMETER, mt);

        Object existingAuthSessionId = request.getSession().getAttribute(AUTH_SESSION_ID);

        String currentSessionId = existingAuthSessionId != null ? existingAuthSessionId.toString() : null;

        if (currentSessionId == null || !currentSessionId.equals(session.getSessionId().toString())) {
            log.debug("Setting AUTH_SESSION_ID in session: {}", session.getSessionId());
            request.getSession().setAttribute(AUTH_SESSION_ID, session.getSessionId());
        }

        var cmsData = cmsService.getCmsInfoFromSession(session);

        model.addAttribute("firstName", changeProfileDto.getFirstName());
        model.addAttribute("lastName", changeProfileDto.getLastName());
        model.addAttribute("email", changeProfileDto.getEmail());
        model.addAttribute("confirmEmail", changeProfileDto.getConfirmEmail());
        model.addAttribute("phoneNumber", changeProfileDto.getPhoneNumber());
        model.addAttribute("secondaryPhoneNumber", changeProfileDto.getSecondaryPhoneNumber());

        model.addAttribute("logo", cmsData.getOrDefault("loginLogoPath", ""));
        model.addAttribute("planName", cmsData.getOrDefault("planName", ""));
        model.addAttribute("planFaviconPath", cmsData.getOrDefault("planFaviconPath", ""));

        model.addAttribute("metaTitle", "");

        model.addAttribute("progressSidebarLinks", List.of(cmsData.getOrDefault("progressBarLinkHTML1", ""), cmsData.getOrDefault("progressBarLinkHTML2", ""), cmsData.getOrDefault("progressBarLinkHTML3", ""), cmsData.getOrDefault("progressBarLinkHTML4", ""), cmsData.getOrDefault("progressBarLinkHTML5", ""), cmsData.getOrDefault("progressBarLinkHTML6", "")));

        model.addAttribute("progressSidebarSteps", List.of(cmsData.getOrDefault("profileContactInformationNavLink1", "")));

        model.addAttribute("contact", cmsData.getOrDefault("helpContentHTML", ""));
        model.addAttribute("title", cmsData.getOrDefault("profileContactInformationTitle", ""));
        model.addAttribute("subtitle", cmsData.getOrDefault("profileContactInformationSubTitle", ""));

        model.addAttribute("profileContactInformationEditAccountOwnerName", "Edit Account Owner Name");
        model.addAttribute("profileContactInformationEditEmail", "Edit Email");
        model.addAttribute("profileContactInformationEditPhoneNumbers", "Edit Phone Number(s)");
        model.addAttribute("profileUpdateText", cmsData.getOrDefault("profileUpdateText", ""));
        model.addAttribute("profileContactInformationRequiredText", "All fields are required unless indicated as optional.");

        model.addAttribute("footerLinks", cmsData.getOrDefault("footerLinks", ""));
        model.addAttribute("footerHTML", cmsData.getOrDefault("footerHTML", ""));

        if (request.getSession().getAttribute("error") != null) {
            model.addAttribute("error", request.getSession().getAttribute("error"));
            request.getSession().removeAttribute("error");
        }

        model.addAttribute("backUrlPath", manageProfileService.getRedirect(url, OPERATION_CANCELLED, session));

        model.addAttribute("accessTokenErrorPath", manageProfileService.getRedirect(url, ACCESS_TOKEN_EXPIRED, session));

        return "pages/user/change-profile";
    }

    @PostMapping("/user/change-profile")
    public String updateUserProfile(HttpServletRequest request, @Valid @ModelAttribute ChangeProfileDto changeProfileDto, BindingResult bindingResult, @RequestParam(value = "Authorization", required = false) String authHeader, @RequestParam(value = "x-signature", required = false) String signature, @RequestParam(value = "x-request-datetime", required = false) String requestDatetime, @RequestParam(value = "x-request-id", required = false) String requestId) throws IOException, InvalidUserSessionException, AuthorizationMissingException {

        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        request.getSession().removeAttribute("error");

        var url = (String) request.getSession().getAttribute(SOURCE_RELATIVE_PATH_PARAMETER);
        var mt = (String) request.getSession().getAttribute(METADATA_PARAMETER);

        if (authHeader == null) {
            throw new AuthorizationMissingException("Authorization missing from the request parameter");
        }

        var token = manageProfileService.getTokenFromAuthHeader(authHeader);
        var authSession = manageProfileService.validateSessionFromToken(token);

        if (bindingResult.hasErrors()) {
            String message = bindingResult.getAllErrors().stream().map(ObjectError::getDefaultMessage).distinct().collect(Collectors.joining("\n"));

            request.getSession().setAttribute("changeProfileDto", changeProfileDto);
            return this.redirectToChangeProfilePageWithError(request, url, mt, message, authSession.getBranding());
        }

        try {
            manageProfileService.validateToken(token, authSession);
        } catch (TokenExpiredException e) {
            log.error("Token expired/inactive.", e);
            return "redirect:%s".formatted(manageProfileService.getRedirect(url, ACCESS_TOKEN_EXPIRED, authSession));
        }

        try {
            if (signature == null) {
                throw new SignatureVerificationFailedException("Missing signature.");
            }

            ObjectMapper mapper = new ObjectMapper();
            var body = mapper.writeValueAsString(changeProfileDto);
            var verifiedSignature = signatureService.verifySignature(authHeader, signature, body.getBytes(StandardCharsets.UTF_8));

            if (!verifiedSignature) {
                log.error("Signatures did not match.");
                return "redirect:%s".formatted(manageProfileService.getRedirect(url, INVALID_SIGNATURE, authSession));
            }
        } catch (SignatureVerificationFailedException e) {
            log.error("Signature verification failed.", e);
            return "redirect:%s".formatted(manageProfileService.getRedirect(url, INVALID_SIGNATURE, authSession));
        }

        try {
            manageProfileService.validateSessionFingerprint(authSession, request, requestDatetime);
            manageProfileService.validateRequestDatetime(request, authSession.getApplicationId(), requestDatetime);
        } catch (FingerprintFailedException | InactiveAuthSessionException e) {
            log.error("Fingerprint not valid.", e);
            return "redirect:%s".formatted(manageProfileService.getRedirect(url, INVALID_FINGERPRINT, authSession));
        } catch (RequestDatetimeInvalidException e) {
            log.error("Invalid request datetime.", e);
            return "redirect:%s".formatted(manageProfileService.getRedirect(url, EXPIRED_DATETIME, authSession));
        }

        final var optionalMetadata = metadataService.tryReadFromSessionAsType(request, OutgoingMetadataDto.class);

        if (optionalMetadata.isEmpty()) {
            return this.redirectToChangeProfilePageWithError(request, url, mt, "Something went wrong. Please try again.", authSession.getBranding());
        }

        final var metadata = optionalMetadata.get();

        try {
            String branding = authSession.getBranding();

            var userDetails = userService.getByUsernameAndBranding(authSession.getSubjectId(), branding).orElseThrow(() -> new UserNotFoundException("User not found"));

            String primaryPhone = changeProfileDto.getPhoneNumber();
            changeProfileDto.setPhoneNumber(PhoneNumberUtil.transformPhoneNumberToE164(primaryPhone));

            String secondaryPhone = changeProfileDto.getSecondaryPhoneNumber();
            changeProfileDto.setSecondaryPhoneNumber(PhoneNumberUtil.transformPhoneNumberToE164(secondaryPhone));

            if (Boolean.TRUE.equals(userDetails.profileSyncFlag()) && AccountSyncType.SYNC == accountSyncType) {

                log.info("Syncing profile in Unite and IDP Databases");

                userService.updateForChangeProfilePage(changeProfileDto, userDetails.rowGuid());

                var response = this.onPremAccountService.syncProfile(changeProfileDto, metadata, userDetails, branding, requestId);

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.error("Received a non-200 response from On-Prem Account service while trying to sync profile: {}, body: {}", response.statusCode(), response.body());
                    return this.redirectToChangeProfilePageWithError(request, url, mt, "Something went wrong. Please try again.", authSession.getBranding());
                }

            } else if (Boolean.TRUE.equals(userDetails.profileSyncFlag()) && AccountSyncType.ASYNC == accountSyncType) {

                log.info("Syncing profile in IDP Databases only");

                var sourceHeaders = Map.of(ApplicationConstants.REQUEST_ID_HEADER, requestId, ApplicationConstants.AUTHORIZATION_HEADER, authHeader, ApplicationConstants.REQUEST_DATETIME_HEADER, requestDatetime, ApplicationConstants.REQUEST_VERB_HEADER, HttpVerb.POST.name());

                userService.updateForChangeProfilePageAndNotify(changeProfileDto, userDetails.rowGuid(), branding, metadata, sourceHeaders);

            } else {
                log.warn("Properties sync flag is a value that is not sync or async: {}", accountSyncType);
                userService.updateForChangeProfilePage(changeProfileDto, userDetails.rowGuid());
            }

        } catch (AccountSyncException | UserNotFoundException | UserUpdateFailedException ex) {

            log.error("Error while syncing profile.", ex);
            return this.redirectToChangeProfilePageWithError(request, url, mt, "Something went wrong. Please try again.", authSession.getBranding());
        }

        return "redirect:%s".formatted(manageProfileService.getRedirect(url, authSession));
    }

    String redirectToChangeProfilePageWithError(HttpServletRequest request, String url, String metadata, String errorMessage, String brand) {
        request.getSession().setAttribute("error", errorMessage);

        var newRequestDateTime = Instant.now().atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        String p = "surl=%s&mt=%s&x-request-datetime=%s".formatted(url, metadata, newRequestDateTime);

        var redirectRootUrl = brandUrlMappingService.getUrlByBrand(brand);

        return "redirect:%s/user/change-profile?p=%s".formatted(redirectRootUrl, Base64.getEncoder().encodeToString(p.getBytes()));
    }

    @GetMapping("/user/404")
    public String getNotFoundPage() {
        return "pages/404";
    }

    @ExceptionHandler({InvalidUserSessionSecurityException.class, MissingRequestCookieException.class})
    public ResponseEntity<?> handleSecurityStateException(Exception e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid user session security configuration");
    }

    @ExceptionHandler(InvalidUserSessionParametersException.class)
    public ResponseEntity<?> handleInvalidUserSessionParametersException(InvalidUserSessionParametersException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(AuthorizationMissingException.class)
    public String handleAuthorizationMissing(AuthorizationMissingException e) {
        return "redirect:/404";
    }

    @ExceptionHandler(InvalidUserSessionException.class)
    public String handleInvalidUserSession(InvalidUserSessionException e) {
        return "redirect:/404";
    }

    @ExceptionHandler(ChangeUserProfileBadRequestException.class)
    public ResponseEntity<?> handleChangeUserProfileBadRequestException(ChangeUserProfileBadRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

}

