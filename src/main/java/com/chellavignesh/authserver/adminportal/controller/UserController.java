package com.chellavignesh.authserver.adminportal.controller;

import com.chellavignesh.authserver.adminportal.application.exception.UsernameExistsException;
import com.chellavignesh.authserver.adminportal.externalsource.ExternalSourceService;
import com.chellavignesh.authserver.adminportal.externalsource.exception.InvalidBrandingException;
import com.chellavignesh.authserver.adminportal.metadata.MetadataService;
import com.chellavignesh.authserver.adminportal.metadata.dto.OutgoingMetadataDto;
import com.chellavignesh.authserver.adminportal.organization.exception.OrgGroupNotFoundException;
import com.chellavignesh.authserver.adminportal.organization.exception.OrgNotFoundException;
import com.chellavignesh.authserver.adminportal.toggle.AccountSyncType;
import com.chellavignesh.authserver.adminportal.user.*;
import com.chellavignesh.authserver.adminportal.user.dto.*;
import com.chellavignesh.authserver.adminportal.user.dto.validator.UserDtoValidator;
import com.chellavignesh.authserver.adminportal.user.entity.User;
import com.chellavignesh.authserver.adminportal.user.entity.UserCredentialsWithBranding;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.chellavignesh.authserver.adminportal.user.exception.*;
import com.chellavignesh.authserver.adminportal.user.mapper.UserDtoMapper;
import com.chellavignesh.authserver.adminportal.util.BaseRequestPayloadParser;
import com.chellavignesh.authserver.adminportal.util.EntityValidator;
import com.chellavignesh.authserver.adminportal.util.HttpVerb;
import com.chellavignesh.authserver.adminportal.util.SkipInitBinder;
import com.chellavignesh.authserver.cms.BrandUrlMappingService;
import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.security.PasswordValidatorService;
import com.chellavignesh.authserver.security.exception.PasswordValidationException;
import com.chellavignesh.libcrypto.dto.BaseRequestObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.*;

@RestController
@RequestMapping("/api/v1/users")
@Slf4j
public class UserController {

    private final UserService userService;
    private final EntityValidator entityValidator;
    private final UserDtoValidator userDtoValidator;
    private final AccountSyncType accountSyncType;
    private final OnPremAccountServiceClient onPremAccountService;

    // Used in Spring EL expressions – do not remove
    private final BrandUrlMappingService brandUrlMappingService;
    private final ExternalSourceService externalSourceService;
    private final PasswordValidatorService passwordValidatorService;
    private final MetadataService metadataService;

    private static final Integer MAX_LIMIT = 10;

    @Autowired
    public UserController(UserService userService, EntityValidator entityValidator, UserDtoValidator userDtoValidator, @Value("${toggles.account.sync.type:sync}") AccountSyncType accountSyncType, OnPremAccountServiceClient onPremAccountService, BrandUrlMappingService brandUrlMappingService, ExternalSourceService externalSourceService, PasswordValidatorService passwordValidatorService, MetadataService metadataService) {
        this.userService = userService;
        this.entityValidator = entityValidator;
        this.userDtoValidator = userDtoValidator;
        this.accountSyncType = accountSyncType;
        this.onPremAccountService = onPremAccountService;
        this.brandUrlMappingService = brandUrlMappingService;
        this.externalSourceService = externalSourceService;
        this.passwordValidatorService = passwordValidatorService;
        this.metadataService = metadataService;
    }

    // ----------------------------------------------------------------------
    // INIT BINDER
    // ----------------------------------------------------------------------

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

    // ----------------------------------------------------------------------
    // CREATE USER
    // ----------------------------------------------------------------------

    @PostMapping
    @SkipInitBinder
    @PreAuthorize("hasPermission('idp-admin-admin','create') and @permissionsService.canCreateUser(authentication, " + "#createUserDto.orgGuid, #createUserDto.groupGuid) or hasPermission('url-permissions', 'http://user POST')")
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserDto createUserDto, @SessionAttribute(value = ApplicationConstants.BRANDING_INFO, required = false) String brand) throws OrgNotFoundException, OrgGroupNotFoundException, UserCreationFailedException, InvalidBrandingException {

        createUserDto.setOrgId(entityValidator.validateOrganizationId(createUserDto.getOrgGuid()));

        int groupId = 0;
        if (createUserDto.getGroupGuid() != null && !StringUtils.isEmpty(createUserDto.getGroupGuid().toString())) {

            groupId = entityValidator.validateOrganizationGroupId(createUserDto.getOrgId(), createUserDto.getGroupGuid());
        } else {
            log.info("Group Guid from request was null, set to Group GUID: {}", createUserDto.getGroupGuid());
        }

        createUserDto.setPassword(new String(Base64.getDecoder().decode(createUserDto.getPassword())));

        log.info("Password has been set for user");

        User user = userService.create(createUserDto, groupId, false);

        log.info("Successfully created user");

        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.LOCATION, brandUrlMappingService.getUrlByBrand(brand) + "/api/v1/users/" + user.getRowGuid().toString()).body(new CreateUserResponse(user.getRowGuid()));
    }

    // ----------------------------------------------------------------------
    // CREATE USER PROFILE
    // ----------------------------------------------------------------------

    @PostMapping("/profile")
    @PreAuthorize("hasPermission('idp-admin-admin','create') or hasPermission('url-permissions','http://user POST')")
    public ResponseEntity<?> createUserProfile(@RequestBody String jsonPayload, @RequestHeader Map<String, String> headers) throws UserCreationBadRequestException, OrgNotFoundException, UserCreationFailedException, InvalidBrandingException, UserSyncFailedException {

        BaseRequestObject baseRequestObject = BaseRequestPayloadParser.parsePayload(jsonPayload, CreateUserProfileDto.class);

        CreateUserProfileDto createUserProfileDto = (CreateUserProfileDto) baseRequestObject.getPayload();

        BindException bindingResult = new BindException(createUserProfileDto, "createUserProfileDto");

        userDtoValidator.validate(createUserProfileDto, bindingResult);

        if (bindingResult.hasErrors()) {
            String message = "Error in field " + Objects.requireNonNull(bindingResult.getFieldError()).getField() + " : " + bindingResult.getFieldError().getDefaultMessage();

            throw new UserCreationBadRequestException(message);
        }

        final var externalSource = externalSourceService.findBySourceCode(createUserProfileDto.getBranding());

        if (externalSource.isEmpty() || !externalSource.get().getExternalType().syncSchema().equals(baseRequestObject.getSchema())) {

            throw new InvalidBrandingException("Invalid branding: " + createUserProfileDto.getBranding() + ".");
        }

        CreateUserDto createUserDto = UserDtoMapper.Instance.toCreateUserDto(createUserProfileDto);

        createUserDto.setOrgId(entityValidator.validateOrganizationId(createUserDto.getOrgGuid()));

        CreateUserNotificationConfig notificationConfig = new CreateUserNotificationConfig();

        notificationConfig.setAlwaysSendNotification(true);

        User user = userService.createOrReactivateUser(createUserProfileDto, createUserDto, headers, externalSource.get(), notificationConfig);

        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.LOCATION, brandUrlMappingService.getUrlByBrand(createUserProfileDto.getBranding()) + "/api/v1/users/profile/" + user.getRowGuid().toString()).body(new CreateUserResponse(user.getRowGuid()));
    }
    // ----------------------------------------------------------------------
// UPDATE USER PROFILE
// ----------------------------------------------------------------------

    @PutMapping("/{userGuid}/profile")
    @SkipInitBinder
    @PreAuthorize("hasPermission('idp-admin-admin','update') and @permissionsService.canEditOrDeleteUser(authentication, #userGuid) or @permissionsService.isSelf(authentication, #userGuid) or hasPermission('url-permissions','http://user PUT')")
    public ResponseEntity<?> updateUser(@Valid @RequestBody UpdateUserDto updateUserDto, @Valid @PathVariable UUID userGuid, @RequestHeader Map<String, String> headers, HttpServletRequest request) throws UserCreationBadRequestException, InvalidBrandingException, UserUpdateFailedException, UserNotFoundException, AccountSyncException, UserSyncFailedException {

        BindException bindingResult = new BindException(updateUserDto, "updateUserDto");

        userDtoValidator.validate(updateUserDto, bindingResult);

        if (bindingResult.hasErrors()) {
            String message = "Error in field " + bindingResult.getFieldError().getField() + " : " + bindingResult.getFieldError().getDefaultMessage();

            throw new UserCreationBadRequestException(message);
        }

        final var oExternalSource = externalSourceService.findBySourceCode(updateUserDto.getBranding());

        if (oExternalSource.isEmpty()) {
            throw new InvalidBrandingException("Invalid branding: " + updateUserDto.getBranding() + ".");
        }

        final var optionalMetadata = metadataService.tryReadFromSessionAsType(request, OutgoingMetadataDto.class);

        log.info("PutMapping: userGuid/profile: {}", userGuid);

        User user = userService.update(updateUserDto, userGuid, optionalMetadata.orElse(null), headers);

        updateUserDto.setMemberId(user.getRowGuid());
        updateUserDto.setLoginId(user.getLoginId());

        return ResponseEntity.ok(updateUserDto);
    }

// ----------------------------------------------------------------------
// UPDATE USER EMAIL
// ----------------------------------------------------------------------

    @PutMapping("/{userGuid}/email")
    @SkipInitBinder
    @PreAuthorize("hasPermission('idp-admin-admin','update') and @permissionsService.canEditOrDeleteUser(authentication, #userGuid) or hasPermission('url-permissions','http://user PUT') or @permissionsService.isSelf(authentication, #userGuid)")
    public ResponseEntity<?> updateUserEmail(@Valid @RequestBody UpdateUserEmailDto updateUserEmailDto, @Valid @PathVariable UUID userGuid, @RequestHeader Map<String, String> headers, HttpServletRequest request) throws UserUpdateFailedException, InvalidBrandingException, UserNotFoundException, AccountSyncException, UserSyncFailedException {

        final var oExternalSource = externalSourceService.findBySourceCode(updateUserEmailDto.getBranding());

        if (oExternalSource.isEmpty()) {
            throw new InvalidBrandingException("Invalid branding: " + updateUserEmailDto.getBranding() + ".");
        }

        UserDetails userDetails = userService.getByGuidAndBranding(userGuid, updateUserEmailDto.getBranding()).orElseThrow(() -> new UserNotFoundException("User not found for the branding"));

        headers.put(ApplicationConstants.REQUEST_VERB_HEADER, HttpVerb.PUT.name());

        final var optionalMetadata = metadataService.tryReadFromSessionAsType(request, OutgoingMetadataDto.class);

        final var metadata = optionalMetadata.orElse(null);

        userService.updateEmail(updateUserEmailDto, userDetails, metadata, headers);

        return ResponseEntity.ok(updateUserEmailDto);
    }

// ----------------------------------------------------------------------
// UPDATE USER PASSWORD
// ----------------------------------------------------------------------

    @PutMapping("/{userGuid}/password")
    @SkipInitBinder
    @PreAuthorize("hasPermission('idp-admin-admin','update') and @permissionsService.canEditOrDeleteUser(authentication, #userGuid) or hasPermission('url-permissions','http://user PUT') or @permissionsService.isSelf(authentication, #userGuid)")
    public ResponseEntity<?> updateUserPassword(@Valid @RequestBody NotifiableUpdateUserPasswordDto updateUserPasswordDto, @Valid @PathVariable UUID userGuid, @RequestHeader Map<String, String> headers) throws UserUpdateFailedException, InvalidBrandingException, PasswordValidationException, UserNotFoundException, AccountSyncException {

        final var oExternalSource = externalSourceService.findBySourceCode(updateUserPasswordDto.getBranding());

        if (oExternalSource.isEmpty()) {
            throw new InvalidBrandingException("Invalid branding: " + updateUserPasswordDto.getBranding() + ".");
        }

        var user = userService.getByGuidAndBranding(userGuid, updateUserPasswordDto.getBranding()).orElseThrow(() -> new UserNotFoundException("User not found for the branding"));

        updateUserPasswordDto.setPassword(new String(Base64.getDecoder().decode(updateUserPasswordDto.getPassword())));

        passwordValidatorService.validatePassword(updateUserPasswordDto.getPassword(), userGuid);

        if (user.credSyncFlag() && AccountSyncType.SYNC == accountSyncType && !"idp".equals(updateUserPasswordDto.getIntent().getId())) {

            onPremAccountService.syncPassword(updateUserPasswordDto.getPassword(), null, user, updateUserPasswordDto.getBranding(), null);

        } else if (user.credSyncFlag() && AccountSyncType.ASYNC == accountSyncType) {

            headers.put(ApplicationConstants.REQUEST_VERB_HEADER, HttpVerb.PUT.name());

            userService.updatePasswordAndNotify(updateUserPasswordDto, userGuid, false, headers);
        } else {
            userService.updatePassword(updateUserPasswordDto, userGuid);
        }

        return ResponseEntity.noContent().build();
    }

// ----------------------------------------------------------------------
// UPDATE USERNAME
// ----------------------------------------------------------------------

    @PutMapping("/{userGuid}/username")
    @SkipInitBinder
    @PreAuthorize("hasPermission('idp-admin-admin','update') and @permissionsService.canEditOrDeleteUser(authentication, #userGuid) or hasPermission('url-permissions','http://user PUT')")
    public ResponseEntity<?> updateUsername(@Valid @RequestBody UpdateUsernameDto dto, @Valid @PathVariable UUID userGuid, @RequestHeader Map<String, String> headers) throws UserUpdateFailedException, InvalidBrandingException, UsernameExistsException, UserNotFoundException {

        final var oExternalSource = externalSourceService.findBySourceCode(dto.getBranding());

        if (oExternalSource.isEmpty()) {
            throw new InvalidBrandingException("Invalid branding: " + dto.getBranding() + ".");
        }

        userService.getByGuidAndBranding(userGuid, dto.getBranding()).orElseThrow(() -> new UserNotFoundException("User not found for the branding"));

        Optional<UserCredentialsWithBranding> credentials = userService.getCredentialsByUsernameAndBranding(dto.getUsername(), dto.getBranding());

        if (credentials.isPresent()) {
            throw new UsernameExistsException("The requested username already exists.");
        }

        headers.put(ApplicationConstants.REQUEST_VERB_HEADER, HttpVerb.PUT.name());

        userService.updateUsername(dto, userGuid, oExternalSource.get(), headers);

        return ResponseEntity.noContent().build();
    }

// ----------------------------------------------------------------------
// UPDATE USER SECURITY SETTINGS
// ----------------------------------------------------------------------

    @PutMapping("/{userGuid}/security-settings")
    @PreAuthorize("hasPermission('idp-admin-admin','update') and @permissionsService.canEditOrDeleteUser(authentication, #userGuid) or @permissionsService.isSelf(authentication, #userGuid) or hasPermission('url-permissions','http://user PUT')")
    public ResponseEntity<?> updateUserSecuritySettings(@Valid @RequestBody UpdateUserSecuritySettingsDto updateUserSecuritySettingsDto, @Valid @PathVariable UUID userGuid, BindingResult bindingResult) throws UserCreationBadRequestException, UserUpdateFailedException {

        if (bindingResult.hasErrors()) {
            String message = "Error in field " + Objects.requireNonNull(bindingResult.getFieldError()).getField() + " : " + bindingResult.getFieldError().getDefaultMessage();

            throw new UserCreationBadRequestException(message);
        }

        userService.updateSecuritySettings(updateUserSecuritySettingsDto, userGuid);

        return ResponseEntity.ok(updateUserSecuritySettingsDto);
    }

    @PutMapping("/{userGuid}/metadata")
    @PreAuthorize("hasPermission('idp-admin-admin', 'update') and @permissionsService.canEditOrDeleteUser(authentication, #userGuid) or hasPermission('url-permissions', 'http://user PUT')")
    public ResponseEntity<?> updateUserMetadata(@Valid @RequestBody UpdateUserMetadataDto updateUserMetadataDto, @Valid @PathVariable UUID userGuid, BindingResult bindingResult) throws UserCreationBadRequestException, UserUpdateFailedException, UserNotFoundException {

        if (bindingResult.hasErrors()) {
            String message = "Error in field " + Objects.requireNonNull(bindingResult.getFieldError()).getField() + ": " + bindingResult.getFieldError().getDefaultMessage();
            throw new UserCreationBadRequestException(message);
        }

        userService.updateMetadata(updateUserMetadataDto, userGuid);
        return ResponseEntity.ok(updateUserMetadataDto);
    }

    @GetMapping("/{userGuid}/metadata")
    @PreAuthorize("(hasPermission('idp-admin-admin', 'read') and @permissionsService.isOrgMemberByUserId(authentication, #userGuid)) or (hasPermission('idp-admin-user', 'read') and @permissionsService.isSubjectNonAdminUser(authentication, #userGuid)) or hasPermission('url-permissions', 'http://user GET')")
    public ResponseEntity<?> getUserMetadata(@Valid @PathVariable UUID userGuid) throws UserNotFoundException {
        return ResponseEntity.ok(userService.getMetadata(userGuid));
    }

    @GetMapping("/{userGuid}")
    @PreAuthorize("(hasPermission('idp-admin-admin', 'read') and @permissionsService.isOrgMemberByUserId(authentication, #userGuid)) or (hasPermission('idp-admin-user', 'read') and @permissionsService.isSubjectNonAdminUser(authentication, #userGuid)) or hasPermission('url-permissions', 'http://user GET') or @permissionsService.isSelf(authentication, #userGuid)")
    public ResponseEntity<?> getUserByGuid(@PathVariable UUID userGuid) {

        Optional<UserDetails> userDetails = userService.getByGuid(userGuid);
        if (userDetails.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found or user does not have permissions.");
        }

        return ResponseEntity.ok(UserResponseDto.fromUser(userDetails.get()));
    }

    @DeleteMapping("/{userGuid}")
    @PreAuthorize("hasPermission('idp-admin-admin', 'delete') and @permissionsService.canEditOrDeleteUser(authentication, #userGuid) or hasPermission('url-permissions', 'http://user DELETE')")
    public ResponseEntity<?> deleteUserByGuid(@PathVariable UUID userGuid) throws UserDeleteFailedException {

        User user = userService.deleteByGuid(userGuid);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found or user does not have permissions.");
        }

        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
    }

    @PutMapping("/{userGuid}/status")
    @PreAuthorize("hasPermission('idp-admin-admin', 'delete') and @permissionsService.canEditOrDeleteUser(authentication, #userGuid) or hasPermission('url-permissions', 'http://user DELETE')")
    public ResponseEntity<?> updateUserStatusByGuid(@Valid @RequestBody UpdateUserStatusDto updateUserStatusDto, @PathVariable UUID userGuid) throws UserDeleteFailedException {

        return userService.updateUserStatusByGuid(updateUserStatusDto.status(), userGuid).map(user -> ResponseEntity.noContent().build()).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found or user does not have permissions."));
    }

    @GetMapping
    @PreAuthorize("hasPermission('idp-admin-admin', 'read') or hasPermission('idp-admin-user', 'read') or hasPermission('url-permissions', 'http://user GET')")
    @AdminOnlyFilter
    public ResponseEntity<?> getUsers(@RequestParam(required = false) Optional<Integer> resultsPerPage, @RequestParam(required = false) Optional<Integer> offset, @RequestParam(required = false) Optional<String> type, @RequestParam(required = false) Optional<String> search, @RequestParam(required = false) Optional<UUID> organizationId, @RequestParam(required = false) Optional<String> status, @Valid @RequestBody(required = false) Optional<BrandingDto> brandingDto, HttpServletRequest request) {

        Optional<Integer> rpp = resultsPerPage.isEmpty() ? Optional.of(MAX_LIMIT) : resultsPerPage;

        Optional<Integer> os = offset.isEmpty() ? Optional.of(0) : offset;

        Optional<Integer> s = Optional.ofNullable(getStatus(status));

        var applyAdminOnlyFilter = request.getAttribute(AdminOnlyFilterAspect.APPLY_ADMIN_ONLY_FILTER);

        if (applyAdminOnlyFilter != null && ((Boolean) applyAdminOnlyFilter)) {
            type = Optional.of("Administrator");
            organizationId = Optional.of((UUID) request.getAttribute(AdminOnlyFilterAspect.ADMIN_ORG_ID));
        }

        final var brandings = brandingDto.map(BrandingDto::getBrandingIds).orElseGet(List::of);

        List<UserDetails> usersDetails = userService.getAll(rpp, os, type, search, organizationId, s, brandings);

        List<UserResponseDto> users = usersDetails.stream().map(UserResponseDto::fromUser).toList();

        return ResponseEntity.ok(UsersResponseDto.from(os.get(), rpp.get(), !usersDetails.isEmpty() ? usersDetails.get(0).results() : 0, users));
    }

    @PostMapping("/validate-password")
    @SkipInitBinder
    @PreAuthorize("hasPermission('url-permissions', 'http://user POST')")
    public ResponseEntity<?> validateUserPassword(@Valid @RequestBody ValidateUserPasswordDto validateUserPasswordDto, @Valid UUID userGuid) {
        return ResponseEntity.ok(validateUserPasswordDto);
    }

    private Integer getStatus(Optional<String> status) {
        if (status.isPresent()) {
            if (status.get().equals("Active")) {
                return 1;
            } else if (status.get().equals("Inactive")) {
                return 0;
            }
        }
        return null;
    }

    @ExceptionHandler(InvalidBrandingException.class)
    public ResponseEntity<?> handleInvalidBrandingExceptions(InvalidBrandingException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(UserPasswordValidationFailedException.class)
    public ResponseEntity<?> handleUserPasswordValidationFailedException(UserPasswordValidationFailedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(PasswordValidationException.class)
    public ResponseEntity<?> handleInvalidPasswordExceptions(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(UserSyncFailedException.class)
    public ResponseEntity<?> handleUserSyncFailedException(UserSyncFailedException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }

}

