package com.chellavignesh.authserver.adminportal.organization.role;

import com.chellavignesh.authserver.security.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class RoleAssignmentService {
    private final RoleRepository roles;
    private final ManagementMutationLock mutations;
    private final ManagementLifecycleGuard lifecycle;
    public RoleAssignmentService(RoleRepository roles, ManagementMutationLock mutations, ManagementLifecycleGuard lifecycle) {
        this.roles=roles;this.mutations=mutations;this.lifecycle=lifecycle;
    }

    @Transactional
    public void assign(UUID org, UUID user, UUID groupOrNull, ActorContext actor) {
        mutations.acquire();
        if(!actor.permissions().contains("idp-admin-group:assign") || (!actor.platformAdmin() && !org.equals(actor.organizationId())))
            throw denied();
        int organization=roles.organization(org);
        // Role rows precede profile/membership row locks, matching the B4 order.
        var role=groupOrNull==null?null:roles.getForUpdate(organization,groupOrNull);
        if(role!=null && !"Active".equals(role.dto().status())) throw new RoleFailure(409);
        var target=lifecycle.requireAssignmentTarget(user,organization);
        if(target.organizationId()!=organization) throw new RoleFailure(404);
        Set<String> next=role==null?Set.of():new HashSet<>(role.dto().permissionKeys());
        Set<String> current=roles.assignedPermissions(target.profileId(),organization);
        if(!actor.permissions().containsAll(next) || (!actor.platformAdmin() &&
                (!actor.permissions().containsAll(current) || current.stream().anyMatch(ManagementAccessPolicy::platformReserved)
                || next.stream().anyMatch(ManagementAccessPolicy::platformReserved)))) throw denied();
        if(user.equals(actor.userId()) && !current.containsAll(next)) throw denied();
        if(next.stream().anyMatch(ManagementAccessPolicy::platformReserved)
                && (role==null || !role.designatedPlatformRole() || !actor.platformAdmin())) throw denied();
        if(!next.contains("idp-admin-org:switch")) lifecycle.requireAdminSurvives(ManagementLifecycleGuard.ChangeIntent.user(user));
        roles.assign(organization,target.profileId(),role==null?null:role.id());
    }
    private AccessDeniedException denied() {return new AccessDeniedException("Role assignment denied");}
}
