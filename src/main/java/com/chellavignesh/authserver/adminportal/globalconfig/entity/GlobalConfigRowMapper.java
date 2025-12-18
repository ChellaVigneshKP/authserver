package com.chellavignesh.authserver.adminportal.globalconfig.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class GlobalConfigRowMapper implements RowMapper<GlobalConfig> {

    @Override
    public GlobalConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
        return GlobalConfig.fromResult(rs);
    }
}
