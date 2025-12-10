package com.chellavignesh.authserver.security.passwordvalidator.repository.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PasswordHistoryDataRowMapper implements RowMapper<PasswordHistoryData> {

    @Override
    public PasswordHistoryData mapRow(ResultSet rs, int rowNum) throws SQLException {
        return PasswordHistoryData.fromResult(rs);
    }
}
