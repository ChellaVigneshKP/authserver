package com.chellavignesh.authserver.adminportal.user.entity;

import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class UserRowMapper implements RowMapper<User> {

    @Override
    public User mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
        return User.fromResult(rs);
    }
}
