package com.chellavignesh.authserver.adminportal.organization.entity;


import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class OrganizationGroupPermissionRowMapper implements RowMapper<OrganizationGroupPermission> {
    @Override
    public OrganizationGroupPermission mapRow(ResultSet rs, int rowNum) throws SQLException {
        return OrganizationGroupPermission.fromResult(rs);
    }
}
