package com.chellavignesh.authserver.adminportal.forgotusername.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class UsernameLookupCriteriaRowMapper implements RowMapper<UsernameLookupCriteria> {
    public UsernameLookupCriteria mapRow(ResultSet rs, int rowNum) throws SQLException {
        return UsernameLookupCriteria.fromResult(rs);
    }
}
