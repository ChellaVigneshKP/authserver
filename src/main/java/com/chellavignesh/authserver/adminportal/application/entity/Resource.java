package com.chellavignesh.authserver.adminportal.application.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Resource {
    private Integer id;
    private UUID rowGuid;
    private Integer orgId;
    private Integer appId;
    private Integer resourceLibraryId;

    public static Resource fromResult(ResultSet result) throws SQLException {
        Resource resource = new Resource();
        resource.setId(result.getInt("ResourceId"));
        resource.setRowGuid(UUID.fromString(result.getString("RowGuid")));
        resource.setOrgId(result.getInt("OrganizationId"));
        resource.setAppId(result.getInt("ApplicationId"));
        resource.setResourceLibraryId(result.getInt("ResourceLibraryId"));
        return resource;
    }
}
