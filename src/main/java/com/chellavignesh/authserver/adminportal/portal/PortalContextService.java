package com.chellavignesh.authserver.adminportal.portal;

import com.chellavignesh.authserver.adminportal.organization.OrganizationService;
import com.chellavignesh.authserver.adminportal.portal.dto.PortalContextDto;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.security.ManagementAccessPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class PortalContextService {
    private final ManagementAccessPolicy policy;
    private final UserService users;
    private final OrganizationService organizations;
    private final String providerMode;

    public PortalContextService(ManagementAccessPolicy policy, UserService users, OrganizationService organizations,
                                @Value("${portal.provider-mode:unconfigured}") String providerMode) {
        if (!Set.of("unconfigured", "simulated", "external").contains(providerMode))
            throw new IllegalArgumentException("Invalid portal provider mode configuration");
        this.policy = policy;
        this.users = users;
        this.organizations = organizations;
        this.providerMode = providerMode;
    }

    public PortalContextDto getContext(Authentication authentication) {
        var actor = policy.requireHumanActor(authentication);
        var bearer = (BearerTokenAuthentication) authentication; // requireHumanActor verified the principal.
        UUID login;
        try {
            login = UUID.fromString(String.valueOf(bearer.getTokenAttributes().get("login_guid")));
        } catch (IllegalArgumentException failure) {
            throw new AccessDeniedException("Access denied");
        }
        var user = users.getByLoginId(login).orElseThrow(() -> new AccessDeniedException("Access denied"));
        if (!actor.userId().equals(user.rowGuid()) || !actor.organizationId().equals(user.orgId())
                || !user.username().equals(bearer.getTokenAttributes().get("sub"))
                || !users.isManagementIdentityActive(actor.userId(), actor.organizationId(), login))
            throw new AccessDeniedException("Access denied");
        var accessible = actor.platformAdmin() ? organizations.getAll() : organizations.getForMember(actor.userId());
        var permissions = actor.permissions();
        return new PortalContextDto(
                new PortalContextDto.Subject(actor.userId(), user.loginId(), user.username(), actor.organizationId()),
                accessible.stream().map(org -> new PortalContextDto.Organization(org.getRowGuid(), org.getName())).toList(),
                permissions.stream().sorted().toList(),
                new PortalContextDto.Capabilities(
                        actor.platformAdmin() && any(permissions, "idp-admin-group:create","idp-admin-group:update","idp-admin-group:delete"),
                        any(permissions, "idp-admin-user:create","idp-admin-user:update","idp-admin-user:delete"),
                        actor.platformAdmin() && any(permissions, "idp-admin-resource:create","idp-admin-resource:update"),
                        providerMode));
    }

    private static boolean any(Set<String> permissions, String... keys) {
        for (var key : keys) if (permissions.contains(key)) return true;
        return false;
    }
}
