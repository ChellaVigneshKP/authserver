package com.chellavignesh.authserver.adminportal.organization.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class OrganizationGroupRowMapper implements RowMapper<OrganizationGroup> {
    @Override
    public OrganizationGroup mapRow(ResultSet rs, int rowNum) throws SQLException {
        return OrganizationGroup.fromResult(rs);
    }
}
