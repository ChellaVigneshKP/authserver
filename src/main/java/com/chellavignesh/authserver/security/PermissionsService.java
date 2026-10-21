package com.chellavignesh.authserver.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Compatibility adapter for existing method-security expressions. */
@Component
public class PermissionsService {
    private final ManagementAccessPolicy policy;
    public PermissionsService(ManagementAccessPolicy policy) { this.policy = policy; }
    public boolean isOrgMember(Authentication authentication, UUID org) { return policy.isOrganizationMember(authentication, org); }
    public boolean isOrgMemberByUserId(Authentication authentication, UUID user) { return policy.canReadUser(authentication, user); }
    public boolean isAscensusAdmin(Authentication authentication) { return policy.isPlatformAdmin(authentication); }
    public boolean isAdmin(Authentication authentication) { return policy.canListUsers(authentication); }
    public boolean isSubjectNonAdminUser(Authentication authentication, UUID user) { return policy.canReadUser(authentication, user); }
    public boolean canCreateUser(Authentication authentication, UUID org, UUID group) { return policy.canCreateUser(authentication, org, group); }
    public boolean canEditOrDeleteUser(Authentication authentication, UUID user) { return policy.canMutateUser(authentication, user, "update"); }
    public boolean isSelf(Authentication authentication, UUID user) { return policy.isSelf(authentication, user); }
}
