package com.chellavignesh.authserver.adminportal.resource.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResourceLibrary {
    private Integer id;
    private UUID rowGuid;
    private String name;
    private String description;
    private String uri;
    private String allowedMethod;
    private String urn;

    public static ResourceLibrary fromResult(ResultSet result) throws SQLException {
        ResourceLibrary rl = new ResourceLibrary();
        rl.setId(result.getInt("ResourceLibraryId"));
        rl.setRowGuid(UUID.fromString(result.getString("RowGuid")));
        rl.setName(result.getString("Name"));
        rl.setDescription(result.getString("Description"));
        rl.setUri(result.getString("Uri"));
        rl.setAllowedMethod(result.getString("AllowedMethod"));
        rl.setUrn(result.getString("Urn"));
        return rl;
    }
}
