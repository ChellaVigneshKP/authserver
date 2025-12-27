package com.chellavignesh.authserver.adminportal.controller;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.PostLogoutRedirectUriService;
import com.chellavignesh.authserver.adminportal.application.RedirectUriService;
import com.chellavignesh.authserver.adminportal.application.TokenSettingsService;
import com.chellavignesh.authserver.adminportal.application.dto.*;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.application.entity.ApplicationDetail;
import com.chellavignesh.authserver.adminportal.application.entity.Resource;
import com.chellavignesh.authserver.adminportal.application.entity.TokenSettings;
import com.chellavignesh.authserver.adminportal.application.exception.*;
import com.chellavignesh.authserver.adminportal.organization.exception.OrgNotFoundException;
import com.chellavignesh.authserver.adminportal.organization.exception.ResourceLibraryNotFoundException;
import com.chellavignesh.authserver.adminportal.util.EntityValidator;
import com.chellavignesh.authserver.adminportal.util.IllegalBooleanArgumentException;
import com.chellavignesh.authserver.config.CustomUrlValidator;
import com.chellavignesh.authserver.security.PermissionsService;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/organizations/{orgGuid}/applications")
public class ApplicationController {

    private static final Logger log = LoggerFactory.getLogger(ApplicationController.class);

    @Value("${server.base-path}")
    private String serverBasePath;

    private final ApplicationService applicationService;
    private final RedirectUriService redirectUriService;
    private final PostLogoutRedirectUriService postLogoutRedirectUriService;
    private final TokenSettingsService tokenSettingsService;
    private final EntityValidator entityValidator;
    private final ApplicationDtoValidator appDtoValidator;
    private final PermissionsService permissionsService;

    @Autowired
    public ApplicationController(ApplicationService applicationService, EntityValidator entityValidator, ApplicationDtoValidator appDtoValidator, RedirectUriService redirectUriService, PostLogoutRedirectUriService postLogoutRedirectUriService, TokenSettingsService tokenSettingsService, PermissionsService permissionsService) {
        this.applicationService = applicationService;
        this.entityValidator = entityValidator;
        this.appDtoValidator = appDtoValidator;
        this.redirectUriService = redirectUriService;
        this.postLogoutRedirectUriService = postLogoutRedirectUriService;
        this.tokenSettingsService = tokenSettingsService;
        this.permissionsService = permissionsService;
    }

    @InitBinder({"createApplicationDto", "updateApplicationDto", "updateTokenSettingsDto"})
    public void initBinder(WebDataBinder binder) {
        binder.setValidator(appDtoValidator);
    }

    @PostMapping
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) " + "and hasPermission('idp-admin-app', 'create')")
    public ResponseEntity<?> createApplication(@Valid @PathVariable UUID orgGuid, @Valid @RequestBody CreateApplicationDto createApplicationDto, BindingResult bindingResult) throws AppCreationBadRequestException, OrgNotFoundException, AppCreationFailedException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);

        if (bindingResult.hasErrors()) {
            String message = "Error in field " + bindingResult.getFieldError().getField() + ": " + bindingResult.getFieldError().getDefaultMessage();
            throw new AppCreationBadRequestException(message);
        }

        Application application = applicationService.create(orgId, createApplicationDto);

        return ResponseEntity.status(HttpStatus.CREATED).header("Location", serverBasePath + "/api/v1/organizations/" + orgGuid + "/applications/" + application.getRowGuid().toString()).body(ApplicationResponseDto.fromApplication(application, orgGuid));
    }


    @GetMapping
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) " + "and hasPermission('idp-admin-app', 'read')")
    public ResponseEntity<List<ApplicationResponseDto>> getApplications(@Valid @PathVariable UUID orgGuid) throws OrgNotFoundException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);

        return ResponseEntity.ok(applicationService.getAll(orgId).stream().map(a -> ApplicationResponseDto.fromApplication(a, orgGuid)).collect(Collectors.toList()));
    }


    @PutMapping("/{appGuid}")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) " + "and hasPermission('idp-admin-app', 'update')")
    public ResponseEntity<?> updateApplication(@Valid @RequestBody UpdateApplicationDto updateApplicationDto, @Valid @PathVariable UUID orgGuid, @PathVariable UUID appGuid) throws OrgNotFoundException, AppNotFoundException, ApplicationDataAccessException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);
        Integer appId = entityValidator.validateApplicationId(orgId, appGuid);

        applicationService.updateApplication(orgId, appId, updateApplicationDto);

        return ResponseEntity.ok(ApplicationResponseDto.fromApplication(applicationService.getById(appId).get(), orgGuid));
    }


    @DeleteMapping("/{appGuid}")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) " + "and hasPermission('idp-admin-app', 'delete')")
    public ResponseEntity<?> deleteApplication(@Valid @PathVariable UUID orgGuid, @Valid @PathVariable UUID appGuid) throws OrgNotFoundException, AppNotFoundException, ApplicationDataAccessException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);
        Integer appId = entityValidator.validateApplicationId(orgId, appGuid);

        return ResponseEntity.ok(applicationService.inactivateApplication(orgId, appId));
    }


    @GetMapping("/{appGuid}")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) " + "and hasPermission('idp-admin-app', 'read')")
    public ResponseEntity<ApplicationDetailResponseDto> getApplication(@Valid @PathVariable UUID orgGuid, @Valid @PathVariable UUID appGuid) throws OrgNotFoundException, AppNotFoundException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);
        Integer appId = entityValidator.validateApplicationId(orgId, appGuid);

        ApplicationDetail applicationDetail = applicationService.getApplicationDetailById(appId).orElseThrow(() -> new AppNotFoundException("Application with ID " + appGuid + " not found"));

        return ResponseEntity.ok(ApplicationDetailResponseDto.fromApplicationDetail(applicationDetail, orgGuid));
    }

    @GetMapping("/{appGuid}/urls")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) " + "and hasPermission('idp-admin-app', 'read')")
    public ResponseEntity<ApplicationUrlResponseDto> getUrls(@Valid @PathVariable UUID orgGuid, @PathVariable UUID appGuid) throws OrgNotFoundException, AppNotFoundException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);
        Integer appId = entityValidator.validateApplicationId(orgId, appGuid);

        Application application = applicationService.getById(appId).get();

        Set<String> redirectUris = applicationService.getApplicationRedirectUris(appId);
        Set<String> logoutRedirectUris = applicationService.getApplicationLogoutRedirectUris(appId);

        return ResponseEntity.ok(ApplicationUrlResponseDto.from(orgGuid, appGuid, application, redirectUris, logoutRedirectUris));
    }


    @PutMapping("/{appGuid}/urls")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) " + "and hasPermission('idp-admin-app', 'update')")
    public ResponseEntity<?> updateUrls(@Valid @RequestBody UpdateApplicationUrlsDto updateApplicationUrlsDto, @Valid @PathVariable UUID orgGuid, @PathVariable UUID appGuid) throws OrgNotFoundException, AppNotFoundException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);
        entityValidator.validateApplicationId(orgId, appGuid);
        Integer appId = applicationService.get(appGuid).get().getId();

        if (orgId == null || StringUtils.isEmpty(orgId.toString()) || appId == null || StringUtils.isEmpty(updateApplicationUrlsDto.getApplicationUrl()) || isEmpty(updateApplicationUrlsDto.getReturnUrls()) || isEmpty(updateApplicationUrlsDto.getLogoutUrls())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Required data is missing.");
        }

        if (!isValidURL(updateApplicationUrlsDto.getApplicationUrl()) || isValidURLs(updateApplicationUrlsDto.getReturnUrls()) || isValidURLs(updateApplicationUrlsDto.getLogoutUrls())) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("URIs incorrect format.");
        }

        try {
            applicationService.updateApplicationUri(orgId, appId, updateApplicationUrlsDto.getApplicationUrl());

            redirectUriService.createRedirectUris(orgId, appId, updateApplicationUrlsDto.getReturnUrls());

            postLogoutRedirectUriService.createPostLogoutRedirectUris(orgId, appId, updateApplicationUrlsDto.getLogoutUrls());

        } catch (Exception e) {
            log.error("Error:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Error");
        }

        return ResponseEntity.ok(updateApplicationUrlsDto);
    }

    @ExceptionHandler({AppNotFoundException.class, OrgNotFoundException.class, TokenSettingsNotFoundException.class})
    public ResponseEntity<String> handleInvalidFileException(Exception e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @PutMapping("/{appGuid}/settings")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) " + "and hasPermission('idp-admin-app', 'update')")
    public ResponseEntity<?> updateSettings(@Valid @RequestBody UpdateTokenSettingsDto updateTokenSettingsDto, @Valid @PathVariable UUID orgGuid, @PathVariable UUID appGuid) throws OrgNotFoundException, AppNotFoundException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);
        Integer appId = entityValidator.validateApplicationId(orgId, appGuid);

        try {
            if (Boolean.TRUE.equals(tokenSettingsService.exists(orgId, appId))) {
                tokenSettingsService.updateSettings(orgId, appId, updateTokenSettingsDto);
            } else {
                TokenSettings tokenSettings = tokenSettingsService.createSettings(orgId, appId, updateTokenSettingsDto);

                return ResponseEntity.ok(TokenSettingsResponseDto.fromTokenSettings(tokenSettings, orgGuid, appGuid));
            }
        } catch (Exception e) {
            log.error("Error while updating token settings for application with id {}: ", appGuid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Error");
        }

        return ResponseEntity.ok(TokenSettingsResponseDto.fromTokenSettings(tokenSettingsService.getForApp(orgId, appId).get(), orgGuid, appGuid));
    }

    @PostMapping("/{appGuid}/endpoints")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) " + "and hasPermission('idp-admin-app', 'assign-resource')")
    public ResponseEntity<?> assignResource(@Valid @PathVariable UUID orgGuid, @Valid @PathVariable UUID appGuid, @Valid @RequestBody AssignResourceDto assignResourceDto) throws OrgNotFoundException, AppNotFoundException, ResourceLibraryNotFoundException, ResourceCreationFailedException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);
        Integer appId = entityValidator.validateApplicationId(orgId, appGuid);
        Integer resourceLibraryId = entityValidator.validateResourceLibraryId(assignResourceDto.getResourceId());

        Resource resource = applicationService.assignResource(orgId, appId, resourceLibraryId);

        return ResponseEntity.status(HttpStatus.CREATED).header("Location", serverBasePath + "/api/v1/organizations/" + orgGuid + "/applications/" + appGuid + "/endpoints/" + resource.getRowGuid().toString()).body(null);
    }

    @GetMapping("/{appGuid}/endpoints")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) " + "and hasPermission('idp-admin-app', 'read')")
    public ResponseEntity<List<ApplicationResourceResponseDto>> getAllApplicationResources(@Valid @PathVariable UUID orgGuid, @PathVariable UUID appGuid) throws OrgNotFoundException, AppNotFoundException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);
        Integer appId = entityValidator.validateApplicationId(orgId, appGuid);

        return ResponseEntity.ok(applicationService.getAllApplicationResources(appId).stream().map(ApplicationResourceResponseDto::fromApplicationResponse).collect(Collectors.toList()));
    }

    @DeleteMapping("/{appGuid}/endpoints/{resourceLibraryGuid}")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) " + "and hasPermission('idp-admin-app', 'assign-resource')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteApplicationResource(@Valid @PathVariable UUID orgGuid, @Valid @PathVariable UUID appGuid, @Valid @PathVariable UUID resourceLibraryGuid) throws OrgNotFoundException, AppNotFoundException, AppResourceNotFoundException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);
        Integer appId = entityValidator.validateApplicationId(orgId, appGuid);
        Integer appResourceId = entityValidator.validateResourceId(orgId, appId, resourceLibraryGuid);

        applicationService.deleteResource(appResourceId);
    }

    private boolean isValidURLs(List<String> urls) {
        return !urls.stream().allMatch(this::isValidURL);
    }

    private boolean isValidURL(String url) {
        UrlValidator validator = new CustomUrlValidator();
        return validator.isValid(url);
    }

    private boolean isEmpty(List<String> urls) {
        return urls == null || urls.isEmpty() || urls.stream().anyMatch(StringUtils::isEmpty);
    }

    @ExceptionHandler(IllegalBooleanArgumentException.class)
    public ResponseEntity<String> handleIllegalBooleanArgumentException(IllegalBooleanArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}

