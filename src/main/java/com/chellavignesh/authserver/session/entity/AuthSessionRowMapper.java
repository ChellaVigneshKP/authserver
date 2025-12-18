package com.chellavignesh.authserver.session.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class AuthSessionRowMapper implements RowMapper<AuthSession> {
    @Override
    public AuthSession mapRow(ResultSet rs, int rowNum) throws SQLException {
        return AuthSession.fromResult(rs);
    }
}
