package com.chellavignesh.authserver.adminportal.controller;

import com.chellavignesh.authserver.adminportal.application.exception.AppNotFoundException;
import com.chellavignesh.authserver.adminportal.credential.CredentialService;
import com.chellavignesh.authserver.adminportal.credential.dto.CredentialMetadataDto;
import com.chellavignesh.authserver.adminportal.organization.exception.OrgNotFoundException;
import com.chellavignesh.authserver.adminportal.util.PortalProblem;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/organizations/{orgGuid}/applications/{appGuid}/credentials")
public class CredentialMetadataController {
    private final CredentialService credentials;
    public CredentialMetadataController(CredentialService credentials) { this.credentials = credentials; }

    @GetMapping
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) and hasPermission('idp-admin-app', 'read')")
    public ResponseEntity<List<CredentialMetadataDto>> list(@PathVariable UUID orgGuid, @PathVariable UUID appGuid)
            throws OrgNotFoundException, AppNotFoundException {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(credentials.listMetadata(orgGuid, appGuid));
    }

    @ExceptionHandler({OrgNotFoundException.class, AppNotFoundException.class})
    public ResponseEntity<PortalProblem> notFound() {
        return problem(404, "NOT_FOUND", "Credential scope not found");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<PortalProblem> accessDenied() {
        return problem(403, "ACCESS_DENIED", "Access denied");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<PortalProblem> malformedIdentifier() {
        return problem(400, "INVALID_REQUEST", "Invalid credential scope identifier");
    }

    private ResponseEntity<PortalProblem> problem(int status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(new PortalProblem(code, message, UUID.randomUUID().toString(), false));
    }

}
