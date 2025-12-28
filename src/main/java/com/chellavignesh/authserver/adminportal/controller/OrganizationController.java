package com.chellavignesh.authserver.adminportal.controller;

import com.chellavignesh.authserver.adminportal.organization.OrganizationService;
import com.chellavignesh.authserver.adminportal.organization.dto.*;
import com.chellavignesh.authserver.adminportal.organization.entity.Organization;
import com.chellavignesh.authserver.adminportal.organization.exception.OrgCreationBadRequestException;
import com.chellavignesh.authserver.adminportal.organization.exception.OrgCreationFailedException;
import com.chellavignesh.authserver.adminportal.organization.exception.OrgGroupNotFoundException;
import com.chellavignesh.authserver.adminportal.organization.exception.OrgNotFoundException;
import com.chellavignesh.authserver.adminportal.util.EntityValidator;
import com.chellavignesh.authserver.security.PermissionsService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private static final Logger log = LoggerFactory.getLogger(OrganizationController.class);

    @Value("${server.base-path}")
    private String serverBasePath;

    private final OrganizationService organizationService;
    private final CreateOrganizationDtoValidator orgDtoValidator;
    private final EntityValidator entityValidator;
    private final PermissionsService permissionsService;

    @Autowired
    public OrganizationController(OrganizationService organizationService, CreateOrganizationDtoValidator orgDtoValidator, EntityValidator entityValidator, PermissionsService permissionsService) {
        this.organizationService = organizationService;
        this.orgDtoValidator = orgDtoValidator;
        this.entityValidator = entityValidator;
        this.permissionsService = permissionsService;
    }

    @InitBinder("createOrganizationDto")
    public void initBinder(WebDataBinder binder) {
        binder.setValidator(orgDtoValidator);
    }

    // ----------------------------------------------------------------------
    // CREATE ORGANIZATION
    // ----------------------------------------------------------------------

    @PostMapping
    @PreAuthorize("hasPermission('idp-admin-org','create')")
    public ResponseEntity<?> createOrganization(@Valid @RequestBody CreateOrganizationDto createOrganizationDto, BindingResult bindingResult) throws OrgCreationBadRequestException, OrgCreationFailedException {

        if (bindingResult.hasErrors()) {
            String message = "Error in field " + Objects.requireNonNull(bindingResult.getFieldError()).getField() + " : " + bindingResult.getFieldError().getDefaultMessage();

            throw new OrgCreationBadRequestException(message);
        }

        Organization organization = organizationService.create(createOrganizationDto);

        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.LOCATION, serverBasePath + "/api/v1/organizations/" + organization.getRowGuid()).body(new CreateOrganizationResponse(organization.getRowGuid()));
    }

    // ----------------------------------------------------------------------
    // LIST ORGANIZATIONS
    // ----------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasPermission('idp-admin-org','list')")
    public ResponseEntity<List<OrganizationResponseDto>> getOrganizations() {

        return ResponseEntity.ok(organizationService.getAll().stream().map(OrganizationResponseDto::fromOrganization).collect(Collectors.toList()));
    }

    // ----------------------------------------------------------------------
    // GET ORGANIZATION BY ID
    // ----------------------------------------------------------------------

    @GetMapping("/{id}")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #id) and hasPermission('idp-admin-org','read')")
    public ResponseEntity<?> getOrganization(@PathVariable UUID id) {
        Optional<Organization> organization = organizationService.get(id);

        if (organization.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Organization not found or user does not have permissions.");
        }

        return ResponseEntity.ok(OrganizationResponseDto.fromOrganization(organization.get()));
    }

    // ----------------------------------------------------------------------
    // UPDATE PRIMARY CONTACT
    // ----------------------------------------------------------------------

    @PutMapping("/{orgGuid}/primary-contact")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) and hasPermission('idp-admin-org','update')")
    public ResponseEntity<OrganizationResponseDto> updateApplicationPrimaryContact(@Valid @RequestBody UpdateOrganizationContactDto contact, @Valid @PathVariable UUID orgGuid) throws OrgNotFoundException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);

        organizationService.updatePrimaryContact(orgId, contact);

        return ResponseEntity.ok(OrganizationResponseDto.fromOrganization(organizationService.get(orgGuid).get()));
    }

    // ----------------------------------------------------------------------
    // UPDATE SECONDARY CONTACT
    // ----------------------------------------------------------------------

    @PutMapping("/{orgGuid}/secondary-contact")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) and hasPermission('idp-admin-org','update')")
    public ResponseEntity<OrganizationResponseDto> updateApplicationSecondaryContact(@Valid @RequestBody UpdateOrganizationContactDto contact, @Valid @PathVariable UUID orgGuid) throws OrgNotFoundException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);

        organizationService.updateSecondaryContact(orgId, contact);

        return ResponseEntity.ok(OrganizationResponseDto.fromOrganization(organizationService.get(orgGuid).get()));
    }

    // ----------------------------------------------------------------------
    // UPDATE ORGANIZATION
    // ----------------------------------------------------------------------

    @PutMapping("/{orgGuid}")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) and hasPermission('idp-admin-org','update')")
    public ResponseEntity<OrganizationResponseDto> updateOrganization(@Valid @RequestBody UpdateOrganizationDto updateOrganizationDto, @Valid @PathVariable UUID orgGuid) throws OrgNotFoundException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);

        organizationService.update(orgId, updateOrganizationDto);

        return ResponseEntity.ok(OrganizationResponseDto.fromOrganization(organizationService.get(orgGuid).get()));
    }

    // ----------------------------------------------------------------------
    // GET ORGANIZATION GROUPS
    // ----------------------------------------------------------------------

    @GetMapping("/{orgGuid}/groups")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) and hasPermission('idp-admin-group','read')")
    public ResponseEntity<List<OrganizationGroupResponseDto>> getOrganizationGroups(@Valid @PathVariable UUID orgGuid) throws OrgNotFoundException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);

        return ResponseEntity.ok(organizationService.getOrganizationGroups(orgId).stream().map(OrganizationGroupResponseDto::fromOrganizationGroup).collect(Collectors.toList()));
    }

    // ----------------------------------------------------------------------
    // GET GROUP PERMISSIONS
    // ----------------------------------------------------------------------

    @GetMapping("/{orgGuid}/groups/{groupGuid}/permissions")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) and hasPermission('idp-admin-group','read')")
    public ResponseEntity<List<OrganizationGroupPermissionResponseDto>> getOrganizationGroupPermissions(@Valid @PathVariable UUID orgGuid, @Valid @PathVariable UUID groupGuid) throws OrgNotFoundException, OrgGroupNotFoundException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);

        Integer groupId = entityValidator.validateOrganizationGroupId(orgId, groupGuid);

        return ResponseEntity.ok(organizationService.getOrganizationGroupPermissions(orgId, groupId).stream().map(OrganizationGroupPermissionResponseDto::fromOrganizationGroupPermission).collect(Collectors.toList()));
    }
}
