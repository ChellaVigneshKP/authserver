package com.chellavignesh.authserver.adminportal.resource.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ResourceLibraryRowMapper implements RowMapper<ResourceLibrary> {
    public ResourceLibrary mapRow(ResultSet rs, int rowNum) throws SQLException {
        return ResourceLibrary.fromResult(rs);
    }
}
