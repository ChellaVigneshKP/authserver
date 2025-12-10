package com.chellavignesh.authserver.security.passwordvalidator.repository.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PasswordHistoryData {
    private String password;
    private int version;

    public static PasswordHistoryData fromResult(ResultSet secretResult) throws SQLException {
        return new PasswordHistoryData(secretResult.getString("Password"), secretResult.getInt("Version"));
    }
}
