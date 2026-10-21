package com.chellavignesh.authserver.adminportal.organization.role;

import com.chellavignesh.authserver.adminportal.organization.role.dto.CreateRoleDto;
import com.chellavignesh.authserver.adminportal.organization.role.dto.RoleDto;
import com.chellavignesh.authserver.adminportal.organization.role.dto.UpdateRoleDto;
import com.chellavignesh.authserver.adminportal.util.PortalResponses;
import com.chellavignesh.authserver.security.ManagementAccessPolicy;
import com.chellavignesh.authserver.security.ManagementMutationLock;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{org}/groups")
public class RoleController {
    private final RoleService roles;
    private final ManagementAccessPolicy access;
    private final ManagementMutationLock mutations;

    public RoleController(RoleService roles, ManagementAccessPolicy access, ManagementMutationLock mutations) {
        this.roles = roles;
        this.access = access;
        this.mutations = mutations;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<RoleDto> create(@PathVariable UUID org, @RequestBody CreateRoleDto body,
                                          Authentication authentication) {
        mutations.acquire();
        return ResponseEntity.status(201).body(roles.create(org, body, access.requireHumanActor(authentication)));
    }

    @PutMapping("/{group}")
    @Transactional
    public RoleDto update(@PathVariable UUID org, @PathVariable UUID group, @RequestBody UpdateRoleDto body,
                          Authentication authentication) {
        mutations.acquire();
        return roles.update(org, group, body, access.requireHumanActor(authentication));
    }

    @GetMapping("/{group}")
    public RoleDto get(@PathVariable UUID org, @PathVariable UUID group, Authentication authentication) {
        return roles.get(org, group, access.requireHumanActor(authentication));
    }

    @DeleteMapping("/{group}")
    @Transactional
    public ResponseEntity<Void> deactivate(@PathVariable UUID org, @PathVariable UUID group,
                                           Authentication authentication) {
        mutations.acquire();
        roles.deactivate(org, group, access.requireHumanActor(authentication));
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(RoleFailure.class)
    public ResponseEntity<Void> failure(RoleFailure failure, HttpServletRequest request) {
        if (failure.code() != null) PortalResponses.conflict(request, failure.code(), failure.getMessage());
        return ResponseEntity.status(failure.status()).build();
    }
}
