package com.chellavignesh.authserver.adminportal.util;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SecurityUtil {
    public UUID getTokenUserGuid() {
        UUID guid = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken) && authentication.getPrincipal() != null && authentication.getPrincipal() instanceof OAuth2IntrospectionAuthenticatedPrincipal principal) {
            guid = principal.getAttribute("rowguid");
        }
        return guid;
    }

}
