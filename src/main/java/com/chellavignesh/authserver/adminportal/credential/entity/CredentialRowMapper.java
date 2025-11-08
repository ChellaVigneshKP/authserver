package com.chellavignesh.authserver.adminportal.credential.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class CredentialRowMapper implements RowMapper<Credential> {
    @Override
    public Credential mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Credential.fromResult(rs);
    }
}
