package com.chellavignesh.authserver.adminportal.admin;

import com.chellavignesh.authserver.adminportal.admin.entity.AdminConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AdminRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public AdminRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Optional<AdminConfig> loadAdminConfig(){
        return namedParameterJdbcTemplate.query(
                "{call dbo.GetAdminConfig()}",
                (rs, rowNum) -> AdminConfig.fromResult(rs)
        ).stream().findFirst();
    }
}
