package com.chellavignesh.authserver.adminportal.range.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class RangeRowMapper implements RowMapper<Range> {
    public Range mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Range.fromResult(rs);
    }
}
