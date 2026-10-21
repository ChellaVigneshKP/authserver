package com.chellavignesh.authserver.adminportal.portal;

import com.chellavignesh.authserver.adminportal.organization.role.RoleRepository;
import com.chellavignesh.authserver.adminportal.portal.dto.PermissionCatalogEntryDto;
import com.chellavignesh.authserver.adminportal.portal.dto.PortalContextDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portal")
public class PortalContextController {
    private final PortalContextService contexts;
    private final RoleRepository roles;

    public PortalContextController(PortalContextService contexts, RoleRepository roles) {
        this.contexts = contexts;
        this.roles = roles;
    }

    @GetMapping("/permissions")
    @PreAuthorize("@managementAccessPolicy.hasPlatformPermission(authentication, 'idp-admin-permission:read')")
    public List<PermissionCatalogEntryDto> permissions() { return roles.catalog(); }

    @GetMapping("/context")
    public PortalContextDto context(Authentication authentication) { return contexts.getContext(authentication); }
}
