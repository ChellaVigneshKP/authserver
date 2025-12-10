package com.chellavignesh.authserver.adminportal.user;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

@Aspect
@Component
public class AdminOnlyFilterAspect {
    public static final String APPLY_ADMIN_ONLY_FILTER = "applyAdminOnlyFilter";
    public static final String ADMIN_ORG_ID = "AdminOrgGuid";

    @Around("@annotation(com.chellavignesh.authserver.adminportal.user.AdminOnlyFilter)")
    public Object applyFilter(ProceedingJoinPoint joinPoint) throws Throwable {

        BearerTokenAuthentication authentication = (BearerTokenAuthentication) SecurityContextHolder.getContext().getAuthentication();

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();

        final var permissionsOnUsers = authentication.getTokenAttributes().get("idp-admin-user");
        final var permissionsOnURL = authentication.getTokenAttributes().get("url-permissions");

        var readUsers = false;
        var readURL = false;

        // Turn on admin only filter if user does not have idp-admin-user:read
        // and doesn't have url-permissions:http://user GET permission.
        if (permissionsOnUsers != null) {
            final Object[] userPermissionArray = (Object[]) permissionsOnUsers;
            readUsers = Arrays.asList(userPermissionArray).contains("read");
        }

        if (permissionsOnURL != null) {
            final Object[] urlPermissionArray = (Object[]) permissionsOnURL;
            readURL = Arrays.asList(urlPermissionArray).contains("http://user GET");
        }

        var needAdminOnlyFilter = !(readUsers || readURL);
        request.setAttribute(APPLY_ADMIN_ONLY_FILTER, needAdminOnlyFilter);

        if (needAdminOnlyFilter) {
            request.setAttribute(ADMIN_ORG_ID, authentication.getTokenAttributes().get("org_guid"));
        }

        return joinPoint.proceed();
    }
}
