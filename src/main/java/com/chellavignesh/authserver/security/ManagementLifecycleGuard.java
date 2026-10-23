package com.chellavignesh.authserver.security;

import com.chellavignesh.authserver.adminportal.organization.role.RoleFailure;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/** All callers acquire ManagementMutationLock before resolving mutable identities or intentions. */
@Component
public class ManagementLifecycleGuard {
    private final JdbcTemplate sql;

    public ManagementLifecycleGuard(JdbcTemplate sql) {
        this.sql = sql;
    }

    public record Target(int profileId, int organizationId, UUID organizationGuid) {}

    public boolean hasProfile(UUID user) {
        requireTransaction();
        return user != null && sql.queryForObject("SELECT COUNT(*) FROM Person.Profile WHERE RowGuid=?",
                Integer.class, user.toString()) > 0;
    }

    public void requireUnusedProfileId(UUID user) {
        if (hasProfile(user)) {
            throw RoleFailure.existingProfile();
        }
    }

    public void requireActor(UUID user, UUID organization) {
        requireTransaction();
        if (sql.queryForObject("SELECT COUNT(*) FROM Person.ActiveManagementIdentity WHERE UserGuid=? AND OrganizationGuid=?",
                Integer.class, user.toString(), organization.toString()) != 1) {
            throw new AccessDeniedException("Active unambiguous actor membership required");
        }
    }

    /** Actor and requested organization are authorized before this scoped new-API lookup. */
    public Target requireAssignmentTarget(UUID user, int organization) {
        requireTransaction();
        if (sql.queryForObject("""
                SELECT COUNT(*) FROM Person.Profile p
                JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId AND po.Status=1
                WHERE p.RowGuid=? AND po.OrganizationId=?
                """, Integer.class, user.toString(), organization) == 0) {
            throw new RoleFailure(404);
        }
        return requireTarget(user);
    }

    public Target requireTarget(UUID user) {
        requireTransaction();
        var profiles = sql.queryForList("SELECT ProfileId FROM Person.Profile WITH (UPDLOCK,HOLDLOCK) WHERE RowGuid=?",
                Integer.class, user.toString());
        if (profiles.size() != 1) {
            throw new AccessDeniedException("Management target unavailable");
        }
        int profile = profiles.getFirst();
        if (sql.queryForObject("SELECT COUNT(*) FROM Person.ManagementMembershipIssues WHERE ProfileId=?",
                Integer.class, profile) > 0) {
            throw RoleFailure.membership();
        }
        var memberships = sql.query("""
                SELECT o.OrganizationId,o.RowGuid FROM Person.ProfileOrganization po
                JOIN Partner.Organization o ON o.OrganizationId=po.OrganizationId AND o.Status=1
                WHERE po.ProfileId=? AND po.Status=1
                """, (row, _) -> new Target(profile, row.getInt(1), UUID.fromString(row.getString(2))), profile);
        if (memberships.size() != 1) {
            throw new AccessDeniedException("Active organization membership required");
        }
        return memberships.getFirst();
    }

    /** Predict loss of authority while holding the same cross-instance lock as the write. */
    public record ChangeIntent(UUID user, UUID organization, Integer group, boolean removesAuthority) {
        public static ChangeIntent user(UUID user) {
            return new ChangeIntent(user, null, null, true);
        }

        public static ChangeIntent organization(UUID organization) {
            return new ChangeIntent(null, organization, null, true);
        }

        public static ChangeIntent group(int group) {
            return new ChangeIntent(null, null, group, true);
        }
    }

    public void requireAdminSurvives(ChangeIntent intent) {
        requireTransaction();
        if (!intent.removesAuthority()) {
            return;
        }
        if (intent.organization() != null && sql.queryForObject(
                "SELECT COUNT(*) FROM Partner.Organization WHERE OrganizationId=1 AND RowGuid=?",
                Integer.class, intent.organization().toString()) > 0) {
            throw RoleFailure.lastAdministrator();
        }
        String user = intent.user() == null ? null : intent.user().toString();
        String org = intent.organization() == null ? null : intent.organization().toString();
        int affected = sql.queryForObject("""
                SELECT COUNT(DISTINCT ProfileId) FROM Person.EffectivePlatformAdministrator
                WHERE UserGuid=? OR OrganizationGuid=? OR OrganizationGroupId=?
                """, Integer.class, user, org, intent.group());
        if (affected > 0 && sql.queryForObject("""
                SELECT COUNT(DISTINCT ProfileId) FROM Person.EffectivePlatformAdministrator
                WHERE (? IS NULL OR UserGuid<>?) AND (? IS NULL OR OrganizationGuid<>?)
                AND (? IS NULL OR OrganizationGroupId<>?)
                """, Integer.class, user, user, org, org, intent.group(), intent.group()) == 0) {
            throw RoleFailure.lastAdministrator();
        }
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Lifecycle policy requires a locked transaction");
        }
    }
}
