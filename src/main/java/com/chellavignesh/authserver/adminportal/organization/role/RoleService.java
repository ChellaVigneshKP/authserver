package com.chellavignesh.authserver.adminportal.organization.role;

import com.chellavignesh.authserver.adminportal.organization.role.dto.CreateRoleDto;
import com.chellavignesh.authserver.adminportal.organization.role.dto.RoleDto;
import com.chellavignesh.authserver.adminportal.organization.role.dto.UpdateRoleDto;
import com.chellavignesh.authserver.security.ActorContext;
import com.chellavignesh.authserver.security.ManagementAccessPolicy;
import com.chellavignesh.authserver.security.ManagementMutationLock;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class RoleService {
    private final RoleRepository roles;
    private final ManagementMutationLock mutations;

    public RoleService(RoleRepository roles, ManagementMutationLock mutations) {
        this.roles = roles;
        this.mutations = mutations;
    }

    @Transactional
    public RoleDto create(UUID org, CreateRoleDto dto, ActorContext actor) {
        mutations.acquire();
        require(actor, org, "create");
        int organization = roles.organization(org);
        roles.preflight(organization);
        String name = validate(dto.name(), dto.description(), dto.permissionKeys());
        roles.unique(organization, name, null);
        UUID id = roles.create(organization, name, dto.description(), actor.userId());
        var row = roles.get(organization, id);
        roles.permissions(organization, row.id(), dto.permissionKeys(), actor.userId());
        return roles.get(organization, id).dto();
    }

    @Transactional
    public RoleDto update(UUID org, UUID group, UpdateRoleDto dto, ActorContext actor) {
        mutations.acquire();
        require(actor, org, "update");
        int organization = roles.organization(org);
        var row = roles.getForUpdate(organization, group);
        mutable(row);
        roles.preflight(organization);
        String name = validate(dto.name(), dto.description(), dto.permissionKeys());
        if (dto.revision() == null || dto.revision() < 1) throw new RoleFailure(400);
        if (dto.revision() != row.dto().revision()) throw new RoleFailure(409);
        if (!"Active".equals(row.dto().status())) throw new RoleFailure(409);
        roles.unique(organization, name, row.id());
        roles.update(organization, row, name, dto.description(), dto.revision(), actor.userId());
        roles.permissions(organization, row.id(), dto.permissionKeys(), actor.userId());
        return roles.get(organization, group).dto();
    }

    public RoleDto get(UUID org, UUID group, ActorContext actor) {
        require(actor, org, "read");
        return roles.get(roles.organization(org), group).dto();
    }

    @Transactional
    public void deactivate(UUID org, UUID group, ActorContext actor) {
        mutations.acquire();
        require(actor, org, "delete");
        int organization = roles.organization(org);
        var row = roles.getForUpdate(organization, group);
        mutable(row);
        roles.preflight(organization);
        if (row.dto().memberCount() > 0) throw RoleFailure.assigned(row.dto().memberCount());
        if (!"Active".equals(row.dto().status())) throw new RoleFailure(409);
        roles.deactivate(organization, row, actor.userId());
    }

    private void mutable(RoleRepository.Row role) {
        if (role.builtin()) throw new RoleFailure(409);
        if (role.dto().permissionKeys().stream().anyMatch(ManagementAccessPolicy::platformReserved)) {
            throw RoleFailure.legacyPlatformRole();
        }
    }

    private void require(ActorContext actor, UUID org, String action) {
        if ((!"read".equals(action) && !actor.platformAdmin())
                || (!actor.platformAdmin() && !actor.organizationId().equals(org))
                || !actor.permissions().contains("idp-admin-group:" + action)) {
            throw new AccessDeniedException("Role access denied");
        }
    }

    private String validate(String name, String description, List<String> keys) {
        if (name == null || name.strip().isEmpty() || name.strip().length() > 100
                || name.codePoints().anyMatch(Character::isISOControl)
                || (description != null && description.length() > 1024)
                || keys == null || keys.isEmpty() || keys.size() > 100) {
            throw new RoleFailure(400);
        }
        var allowed = new HashSet<String>();
        roles.catalog().stream().filter(entry -> !entry.platformOnly()).forEach(entry -> allowed.add(entry.key()));
        if (new HashSet<>(keys).size() != keys.size() || !allowed.containsAll(keys)) throw new RoleFailure(400);
        return name.strip();
    }
}
