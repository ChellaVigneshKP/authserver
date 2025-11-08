package com.chellavignesh.authserver.adminportal.application.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ApplicationRowMapper implements RowMapper<Application> {
    public Application mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Application.fromResultSet(rs);
    }
}
