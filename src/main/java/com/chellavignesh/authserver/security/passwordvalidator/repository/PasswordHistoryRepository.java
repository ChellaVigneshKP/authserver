package com.chellavignesh.authserver.security.passwordvalidator.repository;

import com.chellavignesh.authserver.security.passwordvalidator.repository.entity.PasswordHistoryData;
import com.chellavignesh.authserver.security.passwordvalidator.repository.entity.PasswordHistoryDataRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PasswordHistoryRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public PasswordHistoryRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<PasswordHistoryData> getHistoricPasswords(int profileId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("profileId", profileId);

        return namedParameterJdbcTemplate.query(
                "{call Person.getHistoricPasswords(:profileId)}",
                parameters,
                new PasswordHistoryDataRowMapper()
        );
    }
}
