package com.chellavignesh.authserver.adminportal.user.entity;

import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class UserAuthDetailsRowMapper implements RowMapper<UserAuthDetails> {

    @Override
    public UserAuthDetails mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
        return UserAuthDetails.fromResult(rs);
    }
}
