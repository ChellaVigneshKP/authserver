package com.chellavignesh.authserver.adminportal.organization.role;

import com.chellavignesh.authserver.adminportal.organization.role.dto.AssignRoleDto;
import com.chellavignesh.authserver.adminportal.util.PortalResponses;
import com.chellavignesh.authserver.security.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{org}/users/{user}/group")
public class RoleAssignmentController {
    private final RoleAssignmentService assignments;
    private final ManagementAccessPolicy access;
    private final ManagementMutationLock mutations;
    public RoleAssignmentController(RoleAssignmentService assignments, ManagementAccessPolicy access, ManagementMutationLock mutations) {
        this.assignments=assignments;this.access=access;this.mutations=mutations;
    }
    @PutMapping
    @Transactional
    public ResponseEntity<Void> assign(@PathVariable UUID org,@PathVariable UUID user,@RequestBody AssignRoleDto dto,Authentication authentication) {
        mutations.acquire();
        assignments.assign(org,user,dto.groupId(),access.requireHumanActorForMutation(authentication));
        return ResponseEntity.noContent().build();
    }
    @ExceptionHandler(RoleFailure.class)
    public ResponseEntity<Void> failure(RoleFailure failure,HttpServletRequest request) {
        if(failure.code()!=null) PortalResponses.conflict(request,failure.code(),failure.getMessage());
        return ResponseEntity.status(failure.status()).build();
    }
}
