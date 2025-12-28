package com.chellavignesh.authserver.adminportal.controller;

import com.chellavignesh.authserver.adminportal.application.exception.ResourceLibraryDataAccessException;
import com.chellavignesh.authserver.adminportal.organization.exception.ResourceLibraryNotFoundException;
import com.chellavignesh.authserver.adminportal.resource.ResourceLibraryService;
import com.chellavignesh.authserver.adminportal.resource.dto.HttpMethodEnum;
import com.chellavignesh.authserver.adminportal.resource.dto.ResourceLibraryDto;
import com.chellavignesh.authserver.adminportal.resource.dto.ResourceLibraryResponseDto;
import com.chellavignesh.authserver.adminportal.resource.entity.ResourceLibrary;
import com.chellavignesh.authserver.adminportal.resource.exception.ResourceLibraryCreationFailedException;
import com.chellavignesh.authserver.adminportal.util.EntityValidator;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/endpoints")
public class ResourceLibraryController {

    @Value("${server.base-path}")
    private String serverBasePath;

    private final ResourceLibraryService resourceLibraryService;
    private final EntityValidator entityValidator;

    @Autowired
    public ResourceLibraryController(ResourceLibraryService resourceService, EntityValidator entityValidator) {
        this.resourceLibraryService = resourceService;
        this.entityValidator = entityValidator;
    }

    // ----------------------------------------------------------------------
    // CREATE RESOURCE
    // ----------------------------------------------------------------------

    @PostMapping
    @PreAuthorize("hasPermission('idp-admin-resource','create')")
    public ResponseEntity<?> createResourceLibraryItem(@Valid @RequestBody ResourceLibraryDto createResourceLibraryDto) throws ResourceLibraryCreationFailedException {

        ResourceLibrary resourceLibrary = resourceLibraryService.create(createResourceLibraryDto);

        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.LOCATION, serverBasePath + "/api/v1/endpoints/" + resourceLibrary.getRowGuid()).body(ResourceLibraryResponseDto.fromResourceLibrary(resourceLibrary));
    }

    // ----------------------------------------------------------------------
    // LIST RESOURCES
    // ----------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasPermission('idp-admin-resource','read')")
    public ResponseEntity<List<ResourceLibraryResponseDto>> getResourceLibraryItems() {

        return ResponseEntity.ok(resourceLibraryService.getAll().stream().map(ResourceLibraryResponseDto::fromResourceLibrary).collect(Collectors.toList()));
    }

    // ----------------------------------------------------------------------
    // GET RESOURCE BY ID
    // ----------------------------------------------------------------------

    @GetMapping("/{resourceGuid}")
    @PreAuthorize("hasPermission('idp-admin-resource','read')")
    public ResponseEntity<ResourceLibraryResponseDto> getResourceLibrary(@Valid @PathVariable UUID resourceGuid) throws ResourceLibraryNotFoundException {

        Optional<ResourceLibrary> resourceLibrary = resourceLibraryService.get(resourceGuid);

        if (resourceLibrary.isEmpty()) {
            throw new ResourceLibraryNotFoundException("Resource with id: " + resourceGuid + " not found");
        }

        return ResponseEntity.ok(ResourceLibraryResponseDto.fromResourceLibrary(resourceLibrary.get()));
    }

    // ----------------------------------------------------------------------
    // UPDATE RESOURCE
    // ----------------------------------------------------------------------

    @PutMapping("/{resourceGuid}")
    @PreAuthorize("hasPermission('idp-admin-resource','update')")
    public ResponseEntity<?> updateResourceLibrary(@Valid @RequestBody ResourceLibraryDto updateResourceLibraryDto, @Valid @PathVariable UUID resourceGuid) throws ResourceLibraryNotFoundException, ResourceLibraryDataAccessException {

        Integer resourceId = entityValidator.validateResourceLibraryId(resourceGuid);

        resourceLibraryService.update(resourceId, updateResourceLibraryDto);

        return ResponseEntity.ok(ResourceLibraryResponseDto.fromResourceLibrary(resourceLibraryService.get(resourceGuid).get()));
    }

    // ----------------------------------------------------------------------
    // EXCEPTION HANDLER
    // ----------------------------------------------------------------------

    @ExceptionHandler(InvalidFormatException.class)
    public ResponseEntity<String> handleInvalidEnum(InvalidFormatException ex) throws InvalidFormatException {

        if (ex.getTargetType().isAssignableFrom(HttpMethodEnum.class)) {

            return ResponseEntity.badRequest().body(ex.getMessage());
        }

        throw ex;
    }
}

