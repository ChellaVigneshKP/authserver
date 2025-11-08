package com.chellavignesh.authserver.adminportal.forgotusername;

import com.chellavignesh.authserver.adminportal.forgotusername.entity.UsernameLookupField;
import com.chellavignesh.authserver.adminportal.forgotusername.entity.UsernameLookupFieldRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UsernameLookupFieldRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    public UsernameLookupFieldRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<UsernameLookupField> getAll() {
        var parameters = new MapSqlParameterSource()
                .addValue("enumTypeName", "UsernameLookupField");

        return jdbcTemplate.query(
                "{call dbo.GetEnumsByType(:enumTypeName)}",
                parameters,
                new UsernameLookupFieldRowMapper()
        );
    }
}
