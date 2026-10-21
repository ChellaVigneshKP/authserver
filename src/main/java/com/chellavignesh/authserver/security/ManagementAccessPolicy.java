package com.chellavignesh.authserver.security;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.organization.OrganizationService;
import com.chellavignesh.authserver.adminportal.organization.OrganizationStatus;
import com.chellavignesh.authserver.adminportal.organization.entity.Organization;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.UserStatus;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.chellavignesh.authserver.enums.entity.ApplicationTypeEnum;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Server-side tenant policy shared by legacy management APIs and new portal contracts. */
@Component
public class ManagementAccessPolicy {
    private final UserService users;
    private final OrganizationService organizations;
    private final ApplicationService applications;

    public ManagementAccessPolicy(UserService users, OrganizationService organizations, ApplicationService applications) {
        this.users = users;
        this.organizations = organizations;
        this.applications = applications;
    }

    public ActorContext requireHumanActor(Authentication authentication) {
        var principal = principal(authentication);
        UUID userId = userId(principal);
        if (userId == null) throw denied();
        UserDetails member = users.getByGuid(userId).orElseThrow(ManagementAccessPolicy::denied);
        UUID org = uuid(principal.getAttribute("org_guid"));
        if (member.status() != UserStatus.Active || org == null || !org.equals(member.orgId())) throw denied();
        activeOrganization(org);
        if (organizations.getForMember(userId).stream().noneMatch(membership -> org.equals(membership.getRowGuid())))
            throw denied();
        Set<String> permissions = permissions(principal);
        return new ActorContext(userId, org, permissions.contains("idp-admin-org:switch"), permissions);
    }

    public UUID requireOrganizationScope(Authentication authentication, UUID requested) {
        var principal = principal(authentication);
        UUID own;
        boolean platform = false;
        if (userId(principal) != null) {
            var actor = requireHumanActor(authentication);
            own = actor.organizationId();
            platform = actor.platformAdmin();
        } else {
            own = machineOrganization(authentication);
        }
        UUID scope = requested == null ? own : requested;
        if (!platform && !own.equals(scope)) throw denied();
        if (platform) organizations.get(scope).orElseThrow(ManagementAccessPolicy::denied);
        else activeOrganization(scope);
        return scope;
    }

    public boolean isOrganizationMember(Authentication authentication, UUID organizationId) {
        return allowed(() -> requireOrganizationScope(authentication, organizationId) != null);
    }

    public boolean isPlatformAdmin(Authentication authentication) {
        return allowed(() -> requireHumanActor(authentication).platformAdmin());
    }

    public boolean hasPlatformPermission(Authentication authentication, String key) {
        return allowed(() -> {
            var actor = requireHumanActor(authentication);
            return actor.platformAdmin() && actor.permissions().contains(key);
        });
    }

    public boolean canReadUser(Authentication authentication, UUID userGuid) {
        return allowed(() -> {
            var target = target(userGuid);
            requireOrganizationScope(authentication, target.orgId());
            if (isMachine(authentication)) return hasMachinePermission(authentication, "GET");
            var actor = requireHumanActor(authentication);
            return actor.permissions().contains(target.groupId() == null ? "idp-admin-user:read" : "idp-admin-admin:read");
        });
    }

    public boolean canListUsers(Authentication authentication) {
        return allowed(() -> {
            requireOrganizationScope(authentication, null);
            if (isMachine(authentication)) return hasMachinePermission(authentication, "GET");
            var permissions = requireHumanActor(authentication).permissions();
            return permissions.contains("idp-admin-user:read") || permissions.contains("idp-admin-admin:read");
        });
    }

    /** Filter before the SQL query, so both rows and COUNT(*) totals use the same permission scope. */
    public Optional<String> requireUserListType(Authentication authentication, Optional<String> requested) {
        if (!canListUsers(authentication)) throw denied();
        if (isMachine(authentication)) return requested;
        var permissions = requireHumanActor(authentication).permissions();
        boolean ordinary = permissions.contains("idp-admin-user:read");
        boolean admin = permissions.contains("idp-admin-admin:read");
        String forced = ordinary && !admin ? "User" : admin && !ordinary ? "Administrator" : null;
        if (forced != null && requested.isPresent() && !forced.equals(requested.get())) throw denied();
        return forced == null ? requested : Optional.of(forced);
    }

    public void requireUserMutation(Authentication authentication, UUID userGuid, String action) {
        var target = target(userGuid);
        requireOrganizationScope(authentication, target.orgId());
        if (isMachine(authentication)) {
            if (!hasMachinePermission(authentication, verb(action))) throw denied();
            return;
        }
        var actor = requireHumanActor(authentication);
        if (!actor.permissions().contains("idp-admin-user:" + action)) throw denied();
        if (target.groupId() != null) {
            if (!actor.permissions().contains("idp-admin-admin:" + action)) throw denied();
            requireGrantCeiling(actor, target.orgId(), target.groupId());
        }
    }

    public boolean canMutateUser(Authentication authentication, UUID userGuid, String action) {
        return allowed(() -> { requireUserMutation(authentication, userGuid, action); return true; });
    }

    public boolean canCreateUser(Authentication authentication, UUID org, UUID group) {
        return allowed(() -> { requireUserCreation(authentication, org, group); return true; });
    }

    public void requireUserCreation(Authentication authentication, UUID org, UUID group) {
        if (org == null) throw denied();
        requireOrganizationScope(authentication, org);
        if (isMachine(authentication)) {
            if (!hasMachinePermission(authentication, "POST")) throw denied();
            if (group != null) activeGroup(org, group);
            return;
        }
        var actor = requireHumanActor(authentication);
        if (!actor.permissions().contains("idp-admin-user:create")) throw denied();
        if (group != null) {
            if (!actor.permissions().contains("idp-admin-admin:create")) throw denied();
            requireGrantCeiling(actor, org, group);
        }
    }

    /** A synchronized create can reactivate an existing member; the supplied org cannot override its tenant. */
    public void requireUserCreationTarget(Authentication authentication, UUID organizationId, UUID memberId) {
        if (memberId == null) return;
        users.getByGuid(memberId).ifPresent(existing -> {
            if (!existing.orgId().equals(organizationId)) throw denied();
            requireUserMutation(authentication, memberId, "update");
        });
    }

    public boolean canValidatePassword(Authentication authentication, UUID userGuid) {
        return allowed(() -> {
            if (!isMachine(authentication) || !hasMachinePermission(authentication, "POST")) return false;
            requireOrganizationScope(authentication, userGuid == null ? null : target(userGuid).orgId());
            return true;
        });
    }

    public void requireOrganizationUpdate(Authentication authentication, UUID org, String status) {
        requireOrganizationScope(authentication, org);
        var current = organizations.get(org).orElseThrow(ManagementAccessPolicy::denied);
        boolean active = "Active".equalsIgnoreCase(status);
        if (active != (current.getStatus() == OrganizationStatus.ACTIVE)) {
            var actor = requireHumanActor(authentication);
            if (!actor.platformAdmin() || !actor.permissions().contains("idp-admin-org:deactivate")
                    || org.equals(actor.organizationId())) throw denied();
        }
    }

    public boolean isSelf(Authentication authentication, UUID userGuid) {
        return allowed(() -> requireHumanActor(authentication).userId().equals(userGuid));
    }

    private void requireGrantCeiling(ActorContext actor, UUID org, UUID group) {
        var targetGroup = activeGroup(org, group);
        if (actor.platformAdmin()) return;
        var organization = activeOrganization(org);
        Set<String> grants = new HashSet<>();
        organizations.getOrganizationGroupPermissions(organization.getId(), targetGroup.getId())
                .stream().filter(p -> p.getStatus() == OrganizationStatus.ACTIVE).forEach(p -> grants.add(p.getKey()));
        if (!actor.permissions().containsAll(grants) || grants.stream().anyMatch(ManagementAccessPolicy::platformReserved)) throw denied();
    }

    private com.chellavignesh.authserver.adminportal.organization.entity.OrganizationGroup activeGroup(UUID org, UUID group) {
        var organization = activeOrganization(org);
        var result = organizations.getOrganizationGroup(organization.getId(), group).orElseThrow(ManagementAccessPolicy::denied);
        if (result.getStatus() != OrganizationStatus.ACTIVE || !organization.getId().equals(result.getOrgId())) throw denied();
        return result;
    }

    private static boolean platformReserved(String key) {
        return Set.of("idp-admin-org:create", "idp-admin-org:list", "idp-admin-org:switch", "idp-admin-org:deactivate",
                        "idp-admin-group:create", "idp-admin-group:update", "idp-admin-group:delete", "idp-admin-permission:read",
                        "idp-admin-resource:read", "idp-admin-resource:create", "idp-admin-resource:update", "idp-admin-resource:delete", "idp-admin-app:assign-resource",
                        "idp-admin-org-cert:create", "idp-admin-org-cert:update", "idp-admin-org-cert:delete").contains(key);
    }

    private UUID machineOrganization(Authentication authentication) {
        var principal = principal(authentication);
        // Human URL permissions are never a fallback for missing human management claims.
        if (userId(principal) != null || principal.getAttribute("sub") != null) throw denied();
        Object client = principal.getAttribute("client_id");
        if (!(client instanceof String clientId)) throw denied();
        var app = applications.getByClientId(clientId).orElseThrow(ManagementAccessPolicy::denied);
        var organization = organizations.getById(app.getOrgId()).orElseThrow(ManagementAccessPolicy::denied);
        if (!Boolean.TRUE.equals(app.getActive()) || app.getType() != ApplicationTypeEnum.SERVER
                || app.getAuthFlow() == AuthFlowEnum.PKCE || organization.getStatus() != OrganizationStatus.ACTIVE
                || !organization.getRowGuid().equals(uuid(principal.getAttribute("org_guid")))) throw denied();
        return organization.getRowGuid();
    }

    private boolean isMachine(Authentication authentication) {
        return userId(principal(authentication)) == null;
    }

    private boolean hasMachinePermission(Authentication authentication, String verb) {
        machineOrganization(authentication);
        return values(principal(authentication).getAttribute("url-permissions")).contains("http://user " + verb);
    }

    private Organization activeOrganization(UUID org) {
        var result = organizations.get(org).orElseThrow(ManagementAccessPolicy::denied);
        if (result.getStatus() != OrganizationStatus.ACTIVE) throw denied();
        return result;
    }

    private UserDetails target(UUID userGuid) {
        if (userGuid == null) throw denied();
        return users.getByGuid(userGuid).orElseThrow(ManagementAccessPolicy::denied);
    }

    private static OAuth2IntrospectionAuthenticatedPrincipal principal(Authentication authentication) {
        if (!(authentication instanceof BearerTokenAuthentication) || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof OAuth2IntrospectionAuthenticatedPrincipal principal)
                || !Boolean.TRUE.equals(principal.getAttribute("active"))) throw denied();
        return principal;
    }

    private static UUID userId(OAuth2IntrospectionAuthenticatedPrincipal principal) {
        UUID id = uuid(principal.getAttribute("rowguid"));
        if (id == null && principal.getAttribute("metadata") instanceof Map<?, ?> metadata) id = uuid(metadata.get("uuid_member_id"));
        return id;
    }

    private static UUID uuid(Object value) {
        if (value instanceof UUID id) return id;
        try { return value instanceof String text ? UUID.fromString(text) : null; }
        catch (IllegalArgumentException _) { return null; }
    }

    private static Set<String> permissions(OAuth2IntrospectionAuthenticatedPrincipal principal) {
        Set<String> result = new HashSet<>();
        principal.getAttributes().forEach((key, value) -> {
            if (key.startsWith("idp-admin-")) values(value).forEach(action -> result.add(key + ":" + action));
        });
        return result;
    }

    private static Set<String> values(Object value) {
        Collection<?> values = value instanceof Object[] array ? Arrays.asList(array) : value instanceof Collection<?> collection ? collection : List.of();
        Set<String> result = new HashSet<>();
        for (Object item : values) if (item instanceof String text) result.add(text);
        return result;
    }

    private static String verb(String action) {
        return switch (action) { case "create" -> "POST"; case "update" -> "PUT"; case "delete" -> "DELETE"; default -> throw denied(); };
    }

    private static boolean allowed(BooleanSupplier check) {
        try { return check.getAsBoolean(); } catch (AccessDeniedException _) { return false; }
    }

    private static AccessDeniedException denied() { return new AccessDeniedException("Management access denied"); }
}
