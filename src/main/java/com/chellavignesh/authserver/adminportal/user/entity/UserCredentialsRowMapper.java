package com.chellavignesh.authserver.adminportal.user.entity;

import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class UserCredentialsRowMapper implements RowMapper<UserCredentials> {

    @Override
    public UserCredentials mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
        return UserCredentials.fromResult(rs);
    }
}
