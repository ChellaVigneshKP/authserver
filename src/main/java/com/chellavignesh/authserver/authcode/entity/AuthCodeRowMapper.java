package com.chellavignesh.authserver.authcode.entity;


import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class AuthCodeRowMapper implements RowMapper<AuthCode> {
    public AuthCode mapRow(ResultSet rs, int rowNum) throws SQLException {
        return AuthCode.fromResult(rs);
    }
}
