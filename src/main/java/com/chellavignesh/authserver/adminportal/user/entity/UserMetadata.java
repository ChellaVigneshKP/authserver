package com.chellavignesh.authserver.adminportal.user.entity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public record UserMetadata(UUID id, String key, String value) {

    public static UserMetadata fromResult(ResultSet result) throws SQLException {
        UserMetadata userMetadata = null;
        try {
            userMetadata = new UserMetadata(UUID.fromString(result.getString("RowGuid")), result.getString("Key"), result.getString("Value"));
        } catch (SQLException _) {
        }
        return userMetadata;
    }
}
