package com.chellavignesh.authserver.adminportal.application.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ApplicationDetailRowMapper implements RowMapper<ApplicationDetail> {
    public ApplicationDetail mapRow(ResultSet rs, int rowNum) throws SQLException {
        return ApplicationDetail.fromResultSet(rs);
    }
}
