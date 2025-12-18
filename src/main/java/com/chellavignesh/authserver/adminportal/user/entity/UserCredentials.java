package com.chellavignesh.authserver.adminportal.user.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserCredentials {

    private Integer id;
    private Integer profileId;
    private String username;
    private String password;
    private UUID rowGuid;

    public static UserCredentials fromResult(ResultSet result) throws SQLException {
        UserCredentials userCredentials = new UserCredentials();
        try {
            userCredentials.setId(result.getInt("Id"));
            userCredentials.setProfileId(result.getInt("ProfileId"));
            userCredentials.setUsername(result.getString("Username"));
            userCredentials.setPassword(result.getString("Password"));
            userCredentials.setRowGuid(UUID.fromString(result.getString("RowGuid")));
            return userCredentials;
        } catch (SQLException _) {
            return null;
        }
    }
}

