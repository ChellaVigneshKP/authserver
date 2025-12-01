package com.chellavignesh.authserver.authcode;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.authcode.dto.CreateAuthCodeDto;
import com.chellavignesh.authserver.authcode.entity.AuthCode;
import com.chellavignesh.authserver.authcode.entity.AuthCodeRowMapper;
import com.chellavignesh.authserver.authcode.exception.AuthCodeCreationFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class AuthCodeRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ApplicationService applicationService;

    @Autowired
    public AuthCodeRepository(NamedParameterJdbcTemplate jdbcTemplate, ApplicationService applicationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.applicationService = applicationService;
    }

    public AuthCode create(CreateAuthCodeDto dto) throws AuthCodeCreationFailedException {
        var parameters = new MapSqlParameterSource()
                .addValue("applicationId", dto.getApplicationId())
                .addValue("sessionId", dto.getSessionId())
                .addValue("data", dto.getData());
        Optional<Integer> authCodeId = jdbcTemplate.query(
                "{call Token.CreateAuthCode(:applicationId, :sessionId, :data)}", parameters,
                (rs, _) -> rs.getInt("ID")
        ).stream().findFirst();

        if (authCodeId.isPresent()) {
            return getById(authCodeId.get()).get();
        } else {
            throw new AuthCodeCreationFailedException("Failed to create auth code");
        }
    }

    public Optional<AuthCode> getById(Integer id) {
        return jdbcTemplate.query(
                "{call Token.GetAuthCodeById(:id)}",
                new MapSqlParameterSource("id", id),
                new AuthCodeRowMapper()
        ).stream().findFirst();
    }

    public Optional<UUID> getSessionIdByAuthCode(String data) {
        return jdbcTemplate.query(
                "{call Token.GetSessionIdByAuthCode(:data)}",
                new MapSqlParameterSource("data", data),
                (rs, rowNum) -> UUID.fromString(rs.getString("SessionId"))
        ).stream().findFirst();
    }

    public void setConsumedOn(String data) {
        jdbcTemplate.update(
                "{call Token.SetAuthCodeConsumedOn(:data)}",
                new MapSqlParameterSource("data", data)
        );
    }
}
