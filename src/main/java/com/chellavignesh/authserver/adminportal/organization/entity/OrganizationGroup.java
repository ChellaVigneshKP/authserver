package com.chellavignesh.authserver.adminportal.organization.entity;

import com.chellavignesh.authserver.adminportal.organization.OrganizationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrganizationGroup {
    private Integer id;
    private UUID rowGuid;
    private String name;
    private String description;
    private OrganizationStatus status;
    private Integer orgId;
    private int revision;
    private int memberCount;

    public OrganizationGroup(Integer id, UUID rowGuid, String name, String description, OrganizationStatus status, Integer orgId) {
        this(id, rowGuid, name, description, status, orgId, 1, 0);
    }

    public static OrganizationGroup fromResult(ResultSet result) throws SQLException {
        OrganizationGroup group = new OrganizationGroup();
        try {
            group.setId(result.getInt("OrganizationGroupId"));
            group.setRowGuid(UUID.fromString(result.getString("RowGuid")));
            group.setName(result.getString("GroupName"));
            group.setDescription(result.getString("Description"));
            group.setStatus(OrganizationStatus.fromByte(result.getByte("Status")));
            group.setOrgId(result.getInt("OrganizationId"));
            group.setRevision(result.getInt("Revision"));
            // Both list and detail procedures project the live assignment count.
            group.setMemberCount(result.getInt("MemberCount"));
            return group;
        } catch (SQLException e) {
            throw e;
        }
    }
}
