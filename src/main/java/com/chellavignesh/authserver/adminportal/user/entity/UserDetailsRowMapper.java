package com.chellavignesh.authserver.adminportal.user.entity;

import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDetailsRowMapper implements RowMapper<UserDetails> {

    @Override
    public UserDetails mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
        return UserDetails.fromResult(rs);
    }
}
