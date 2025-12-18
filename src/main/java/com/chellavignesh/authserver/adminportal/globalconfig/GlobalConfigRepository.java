package com.chellavignesh.authserver.adminportal.globalconfig;

import com.chellavignesh.authserver.adminportal.globalconfig.entity.GlobalConfig;
import com.chellavignesh.authserver.adminportal.globalconfig.entity.GlobalConfigRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public class GlobalConfigRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public GlobalConfigRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<GlobalConfig> getAll() {
        return namedParameterJdbcTemplate.query(
                "{call dbo.GetGlobalConfig}",
                new MapSqlParameterSource(),
                new GlobalConfigRowMapper()
        );
    }
}
