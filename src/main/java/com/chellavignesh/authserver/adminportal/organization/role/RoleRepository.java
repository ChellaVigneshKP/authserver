package com.chellavignesh.authserver.adminportal.organization.role;

import com.chellavignesh.authserver.adminportal.organization.role.dto.RoleDto;
import com.chellavignesh.authserver.adminportal.portal.dto.PermissionCatalogEntryDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class RoleRepository {
    private final JdbcTemplate sql;

    public RoleRepository(JdbcTemplate sql) { this.sql = sql; }

    public List<PermissionCatalogEntryDto> catalog() {
        return sql.query("SELECT PermissionKey,Description,PlatformOnly FROM dbo.PermissionCatalog ORDER BY PermissionKey",
                (row, _) -> new PermissionCatalogEntryDto(row.getString(1), row.getString(2), row.getBoolean(3)));
    }

    public int organization(UUID org) {
        return sql.query("SELECT OrganizationId FROM Partner.Organization WHERE RowGuid=? AND Status=1",
                        (row, _) -> row.getInt(1), org.toString())
                .stream().findFirst().orElseThrow(() -> new RoleFailure(404));
    }

    public record Row(int id, boolean builtin, RoleDto dto) {}

    public Row get(int org, UUID group) { return get(org, group, false); }

    public Row getForUpdate(int org, UUID group) { return get(org, group, true); }

    private Row get(int org, UUID group, boolean forUpdate) {
        String lock = forUpdate ? " WITH (UPDLOCK, HOLDLOCK)" : "";
        var rows = sql.query("""
                SELECT g.*, (SELECT COUNT(DISTINCT pg.ProfileId) FROM Person.ProfileGroup pg
                WHERE pg.OrganizationGroupId=g.OrganizationGroupId) AS MemberCount
                FROM Partner.OrganizationGroup g%s WHERE g.OrganizationId=? AND g.RowGuid=?
                """.formatted(lock), (row, _) -> new Row(
                row.getInt("OrganizationGroupId"), row.getBoolean("IsBuiltin") || row.getObject("GroupTemplateId") != null,
                new RoleDto(UUID.fromString(row.getString("RowGuid")), row.getString("GroupName"),
                        row.getString("Description"), row.getInt("Status") == 1 ? "Active" : "Inactive",
                        List.of(), row.getInt("Revision"), row.getInt("MemberCount"))), org, group.toString());
        var value = rows.stream().findFirst().orElseThrow(() -> new RoleFailure(404));
        var keys = sql.query("""
                SELECT PermissionKey FROM Partner.OrganizationGroupPermission
                WHERE OrganizationGroupId=? AND OrganizationId=? AND Status=1 ORDER BY PermissionKey
                """, (row, _) -> row.getString(1), value.id(), org);
        var dto = value.dto();
        return new Row(value.id(), value.builtin(), new RoleDto(dto.id(), dto.name(), dto.description(), dto.status(),
                keys, dto.revision(), dto.memberCount()));
    }

    public void preflight(int org) {
        if (sql.queryForObject("SELECT COUNT(*) FROM Partner.RolePreflightIssues WHERE OrganizationId=?", Integer.class, org) > 0) {
            throw new RoleFailure(409);
        }
    }

    public void unique(int org, String name, Integer excluding) {
        if (sql.queryForObject("""
                SELECT COUNT(*) FROM Partner.OrganizationGroup WHERE OrganizationId=?
                AND LOWER(LTRIM(RTRIM(GroupName))) COLLATE Latin1_General_100_CI_AS=LOWER(?) COLLATE Latin1_General_100_CI_AS
                AND (? IS NULL OR OrganizationGroupId<>?)
                """, Integer.class, org, name, excluding, excluding) > 0) {
            throw new RoleFailure(409);
        }
    }

    public UUID create(int org, String name, String description, UUID actor) {
        return UUID.fromString(sql.queryForObject("""
                INSERT Partner.OrganizationGroup(OrganizationId,GroupName,Description,ModifiedBy,IsBuiltin)
                OUTPUT CONVERT(VARCHAR(36),INSERTED.RowGuid) VALUES(?,?,?,?,0)
                """, String.class, org, name, description, actor.toString()));
    }

    public void update(int org, Row row, String name, String description, int revision, UUID actor) {
        if (sql.update("""
                UPDATE Partner.OrganizationGroup SET GroupName=?,Description=?,Revision=Revision+1,
                ModifiedOn=SYSUTCDATETIME(),ModifiedBy=? WHERE OrganizationId=? AND OrganizationGroupId=? AND Revision=?
                """, name, description, actor.toString(), org, row.id(), revision) != 1) {
            throw new RoleFailure(409);
        }
    }

    public void permissions(int org, int group, List<String> keys, UUID actor) {
        sql.update("""
                UPDATE Partner.OrganizationGroupPermission SET Status=0,ModifiedOn=SYSUTCDATETIME(),ModifiedBy=?
                WHERE OrganizationGroupId=? AND OrganizationId=? AND Status=1
                """, actor.toString(), group, org);
        for (String key : keys) {
            int changed = sql.update("""
                    UPDATE Partner.OrganizationGroupPermission SET Status=1,ModifiedOn=SYSUTCDATETIME(),ModifiedBy=?
                    WHERE OrganizationGroupId=? AND OrganizationId=? AND PermissionKey=?
                    """, actor.toString(), group, org, key);
            if (changed == 0) {
                sql.update("""
                        INSERT Partner.OrganizationGroupPermission(OrganizationGroupId,OrganizationId,PermissionName,PermissionKey,ModifiedBy)
                        SELECT ?,?,Description,PermissionKey,? FROM dbo.PermissionCatalog WHERE PermissionKey=?
                        """, group, org, actor.toString(), key);
            }
        }
    }

    public void deactivate(int org, Row row, UUID actor) {
        if (sql.update("""
                UPDATE Partner.OrganizationGroup SET Status=0,Revision=Revision+1,ModifiedOn=SYSUTCDATETIME(),ModifiedBy=?
                WHERE OrganizationId=? AND OrganizationGroupId=? AND Revision=?
                """, actor.toString(), org, row.id(), row.dto().revision()) != 1) {
            throw new RoleFailure(409);
        }
        sql.update("""
                UPDATE Partner.OrganizationGroupPermission SET Status=0,ModifiedOn=SYSUTCDATETIME(),ModifiedBy=?
                WHERE OrganizationGroupId=? AND OrganizationId=?
                """, actor.toString(), row.id(), org);
    }
}
