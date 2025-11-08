package com.chellavignesh.authserver.adminportal.application.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ApplicationSettingsRowMapper implements RowMapper<ApplicationSettings> {
    public ApplicationSettings mapRow(ResultSet rs, int rowNum) throws SQLException {
        return ApplicationSettings.fromResult(rs);
    }
}
