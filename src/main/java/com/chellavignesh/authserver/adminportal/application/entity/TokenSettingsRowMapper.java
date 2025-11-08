package com.chellavignesh.authserver.adminportal.application.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class TokenSettingsRowMapper implements RowMapper<TokenSettings> {
    public TokenSettings mapRow(ResultSet rs, int rowNum) throws SQLException {
        return TokenSettings.fromResult(rs);
    }
}
