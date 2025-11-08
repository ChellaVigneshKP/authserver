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
public class OrganizationGroupPermission {
    private Integer id;
    private UUID rowGuid;
    private String name;
    private String description;
    private String key;
    private OrganizationStatus status;
    private Integer orgId;
    private Integer orgGroupId;

    public static OrganizationGroupPermission fromResult(ResultSet result) throws SQLException {
        OrganizationGroupPermission permission = new OrganizationGroupPermission();
        try {
            permission.setId(result.getInt("OrganizationGroupPermissionId"));
            permission.setOrgId(result.getInt("OrganizationId"));
            permission.setOrgGroupId(result.getInt("OrganizationGroupId"));
            permission.setRowGuid(UUID.fromString(result.getString("RowGuid")));
            permission.setName(result.getString("PermissionName"));
            permission.setDescription(result.getString("Description"));
            permission.setKey(result.getString("PermissionKey"));
            permission.setStatus(OrganizationStatus.fromByte(result.getByte("Status")));
            return permission;
        } catch (SQLException _) {
            return null;
        }
    }
}
