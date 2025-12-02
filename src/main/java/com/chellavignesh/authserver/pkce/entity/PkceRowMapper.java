package com.chellavignesh.authserver.pkce.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PkceRowMapper implements RowMapper<Pkce> {
    public Pkce mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Pkce.fromResult(rs);
    }
}
