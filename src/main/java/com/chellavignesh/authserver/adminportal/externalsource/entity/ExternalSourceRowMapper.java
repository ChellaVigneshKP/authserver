package com.chellavignesh.authserver.adminportal.externalsource.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ExternalSourceRowMapper implements RowMapper<ExternalSource> {
    @Override
    public ExternalSource mapRow(ResultSet rs, int rowNum) throws SQLException {
        return ExternalSource.fromResult(rs);
    }
}
