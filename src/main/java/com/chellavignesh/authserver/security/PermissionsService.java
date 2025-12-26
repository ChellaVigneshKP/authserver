package com.chellavignesh.authserver.security;


import com.chellavignesh.authserver.adminportal.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PermissionsService {

    private static final Logger log = LoggerFactory.getLogger(PermissionsService.class);

    private final UserService userService;

    @Autowired
    PermissionsService(UserService userService) {
        this.userService = userService;
    }

    public boolean isOrgMember(Authentication authentication, UUID orgGuid) {
        if (this.isAscensusAdmin(authentication)) {
            return true;
        }

        OAuth2IntrospectionAuthenticatedPrincipal principal;
        try {
            principal = (OAuth2IntrospectionAuthenticatedPrincipal) authentication.getPrincipal();
        } catch (ClassCastException e) {
            // DefaultOAuth2AuthenticatedPrincipal with active = false
            return false;
        }

        UUID authenticatedUserOrg = principal.getAttribute("org_guid");
        String userOrgId = orgGuid == null ? null : orgGuid.toString();

        return authenticatedUserOrg != null && authenticatedUserOrg.toString().equalsIgnoreCase(userOrgId);
    }

    public boolean isOrgMemberByUserId(Authentication authentication, UUID userGuid) {

        if (this.isAscensusAdmin(authentication)) {
            return true;
        }

        OAuth2IntrospectionAuthenticatedPrincipal principal;
        try {
            principal = (OAuth2IntrospectionAuthenticatedPrincipal) authentication.getPrincipal();
        } catch (ClassCastException e) {
            // DefaultOAuth2AuthenticatedPrincipal with active = false
            return false;
        }

        UUID authenticatedUserOrg = principal.getAttribute("org_guid");

        var userDetails = userService.getByGuid(userGuid);
        if (userDetails.isEmpty()) {
            log.error("Trying to edit/delete a non existent user: %s".formatted(userGuid));
            return false;
        }

        return authenticatedUserOrg != null && authenticatedUserOrg.equals(userDetails.get().orgId());
    }


    public boolean isAscensusAdmin(Authentication authentication) {

        OAuth2IntrospectionAuthenticatedPrincipal principal;
        try {
            principal = (OAuth2IntrospectionAuthenticatedPrincipal) authentication.getPrincipal();
        } catch (ClassCastException e) {
            // DefaultOAuth2AuthenticatedPrincipal with active = false
            return false;
        }

        // Only the Ascensus Admin group has this action,
        // so we use it to ensure this group can access all orgs
        var validActions = principal.getClaim("idp-admin-org") == null ? null : Arrays.asList(principal.getClaim("idp-admin-org"));

        return validActions != null && validActions.contains("switch");
    }


    public boolean isAdmin(Authentication authentication) {

        OAuth2IntrospectionAuthenticatedPrincipal principal;
        try {
            principal = (OAuth2IntrospectionAuthenticatedPrincipal) authentication.getPrincipal();
        } catch (ClassCastException e) {
            // DefaultOAuth2AuthenticatedPrincipal with active = false
            return false;
        }

        List<Object> validActions = principal.getClaim("idp-admin-org") == null ? null : Arrays.asList(principal.getClaim("idp-admin-org"));

        boolean isSuperAdmin = validActions != null && validActions.contains("switch");

        String authenticatedUserGroup = Objects.toString(principal.getAttribute("group_guid"), null);

        return isSuperAdmin && authenticatedUserGroup != null;
    }

    public boolean isSubjectNonAdminUser(Authentication authentication, UUID userGuid) {

        var userDetails = userService.getByGuid(userGuid);
        if (userDetails.isEmpty()) {
            log.error("Trying to edit/delete a non existent user: %s".formatted(userGuid));
            return false;
        }

        return userDetails.get().groupId() == null;
    }

    public boolean canCreateUser(Authentication authentication, UUID userOrgId, UUID userGroupId) {

        return this.isOrgMember(authentication, userOrgId) && (this.isAscensusAdmin(authentication) || (this.isAdmin(authentication) && userGroupId != null));
    }

    public boolean canEditOrDeleteUser(Authentication authentication, UUID userGuid) {

        var userDetails = userService.getByGuid(userGuid);
        if (userDetails.isEmpty()) {
            log.error("Trying to edit/delete a non existent user: %s".formatted(userGuid));
            return false;
        }

        return this.isOrgMember(authentication, userDetails.get().orgId()) && (this.isAscensusAdmin(authentication) || (this.isAdmin(authentication) && userDetails.get().groupId() != null));
    }

    public boolean isSelf(Authentication authentication, UUID userGuid) {

        try {
            var principal = (OAuth2IntrospectionAuthenticatedPrincipal) authentication.getPrincipal();

            var metadata = (Map<?, ?>) principal.getAttribute("metadata");

            if (metadata == null) {
                return false;
            }

            return Objects.equals(metadata.get("uuid_member_id"), userGuid);
        } catch (ClassCastException | NullPointerException e) {
            return false;
        }
    }
}

