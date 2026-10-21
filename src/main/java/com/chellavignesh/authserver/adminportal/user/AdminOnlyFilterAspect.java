package com.chellavignesh.authserver.adminportal.user;

import com.chellavignesh.authserver.security.ManagementAccessPolicy;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Optional;

/** Legacy annotation adapter. New list handlers derive scope explicitly before querying. */
@Aspect
@Component
public class AdminOnlyFilterAspect {
    public static final String APPLY_ADMIN_ONLY_FILTER = "applyAdminOnlyFilter";
    public static final String ADMIN_ORG_ID = "AdminOrgGuid";
    private final ManagementAccessPolicy policy;
    public AdminOnlyFilterAspect(ManagementAccessPolicy policy) { this.policy = policy; }
    @Around("@annotation(com.chellavignesh.authserver.adminportal.user.AdminOnlyFilter)")
    public Object applyFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        request.setAttribute(ADMIN_ORG_ID, policy.requireOrganizationScope(authentication, null));
        request.setAttribute(APPLY_ADMIN_ONLY_FILTER, policy.requireUserListType(authentication, Optional.empty()).filter("Administrator"::equals).isPresent());
        return joinPoint.proceed();
    }
}
