package com.chellavignesh.authserver.adminportal.user.entity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserCredentialsWithBranding {

    private Integer id;
    private String username;
    private String branding;
    private String password;
    private UUID rowGuid;

    public static UserCredentialsWithBranding fromResult(ResultSet result) throws SQLException {

        UserCredentialsWithBranding userCredentials = new UserCredentialsWithBranding();

        userCredentials.setId(result.getInt("Id"));
        userCredentials.setUsername(result.getString("Username"));
        userCredentials.setPassword(result.getString("Password"));
        userCredentials.setBranding(result.getString("Branding"));
        userCredentials.setRowGuid(UUID.fromString(result.getString("RowGuid")));

        return userCredentials;
    }
}
