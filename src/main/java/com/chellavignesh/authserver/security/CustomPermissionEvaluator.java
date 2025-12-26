package com.chellavignesh.authserver.security;

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {

        String perm = (String) permission;
        String claim = (String) targetDomainObject;
        OAuth2IntrospectionAuthenticatedPrincipal principal;

        if (!authentication.isAuthenticated()) {
            return false;
        }

        try {
            principal = (OAuth2IntrospectionAuthenticatedPrincipal) authentication.getPrincipal();
        } catch (ClassCastException _) {
            // DefaultOAuth2AuthenticatedPrincipal with active = false
            return false;
        }

        String username = principal.getName();

        // First 2 if blocks are in case we want something as claim value
        // other than list of actions
        // maybe remove in the future
        if (Objects.equals(perm, "true")) {
            return principal.hasClaim(claim);
        } else if (Objects.equals(perm, "false")) {
            return !principal.hasClaim(claim);
        } else {
            try {
                List<Object> validActions = principal.getClaim(claim) == null ? null : Arrays.asList(principal.getClaim(claim));

                if (validActions == null || validActions.isEmpty()) {
                    return false;
                }

                return validActions.contains(perm);
            } catch (ClassCastException _) {
                return false;
            }
        }
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        // Shouldn't be used
        return false;
    }
}

