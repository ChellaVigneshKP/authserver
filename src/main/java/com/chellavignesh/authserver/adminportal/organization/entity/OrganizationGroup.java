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

    public static OrganizationGroup fromResult(ResultSet result) throws SQLException {
        OrganizationGroup group = new OrganizationGroup();
        try {
            group.setId(result.getInt("OrganizationGroupId"));
            group.setRowGuid(UUID.fromString(result.getString("RowGuid")));
            group.setName(result.getString("GroupName"));
            group.setDescription(result.getString("Description"));
            group.setStatus(OrganizationStatus.fromByte(result.getByte("Status")));
            group.setOrgId(result.getInt("OrganizationId"));
            return group;
        } catch (SQLException _) {
            return null;
        }
    }
}
