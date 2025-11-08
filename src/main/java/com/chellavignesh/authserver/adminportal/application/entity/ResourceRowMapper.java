package com.chellavignesh.authserver.adminportal.application.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ResourceRowMapper implements RowMapper<Resource> {
    public Resource mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Resource.fromResult(rs);
    }
}
