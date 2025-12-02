package com.chellavignesh.authserver.pkce;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.pkce.dto.CreatePkceDto;
import com.chellavignesh.authserver.pkce.entity.Pkce;
import com.chellavignesh.authserver.pkce.entity.PkceRowMapper;
import com.chellavignesh.authserver.pkce.exception.PkceCreationFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class PkceRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ApplicationService applicationService;

    @Autowired
    public PkceRepository(NamedParameterJdbcTemplate jdbcTemplate, ApplicationService applicationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.applicationService = applicationService;
    }

    public Pkce create(CreatePkceDto dto) throws PkceCreationFailedException {
        var parameters = new MapSqlParameterSource()
                .addValue("applicationId", dto.getApplicationId())
                .addValue("sessionId", dto.getSessionId())
                .addValue("data", dto.getData())
                .addValue("algorithm", dto.getAlgorithm())
                .addValue("redirectUri", dto.getRedirectUri());

        Optional<Integer> pkceId = jdbcTemplate.query(
                "{call Token.CreatePkce(:applicationId, :sessionId, :data, :algorithm, :redirectUri)}",
                parameters,
                (rs, _) -> rs.getInt("ID")
        ).stream().findFirst();

        if (pkceId.isPresent()) {
            return getById(pkceId.get()).get();
        } else {
            throw new PkceCreationFailedException("Could not fetch newly created PKCE record by ID.");
        }
    }

    public Optional<Pkce> getById(Integer id) {
        return jdbcTemplate.query(
                "{call Token.GetPkceById(:id)}",
                new MapSqlParameterSource("id", id),
                new PkceRowMapper()
        ).stream().findFirst();
    }

    public Optional<Pkce> getBySessionId(UUID sessionId) {
        return jdbcTemplate.query(
                "{call Token.GetPkceBySessionId(:sessionId)}",
                new MapSqlParameterSource("sessionId", sessionId),
                new PkceRowMapper()
        ).stream().findFirst();
    }
}
