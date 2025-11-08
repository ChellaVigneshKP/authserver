package com.chellavignesh.authserver.adminportal.forgotusername.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class UsernameLookupFieldRowMapper implements RowMapper<UsernameLookupField> {
    public UsernameLookupField mapRow(ResultSet rs, int rowNum) throws SQLException {
        return UsernameLookupField.fromResult(rs);
    }
}
