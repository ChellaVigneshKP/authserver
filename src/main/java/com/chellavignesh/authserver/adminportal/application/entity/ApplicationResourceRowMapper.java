package com.chellavignesh.authserver.adminportal.application.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ApplicationResourceRowMapper implements RowMapper<ApplicationResource> {
    public ApplicationResource mapRow(ResultSet rs, int rowNum) throws SQLException {
        return ApplicationResource.fromResultSet(rs);
    }
}
