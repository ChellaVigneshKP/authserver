package com.chellavignesh.authserver.adminportal.user.entity;

import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class UserMetadataRowMapper implements RowMapper<UserMetadata> {

    @Override
    public UserMetadata mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
        return UserMetadata.fromResult(rs);
    }
}
