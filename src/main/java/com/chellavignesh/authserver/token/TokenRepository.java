package com.chellavignesh.authserver.token;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import com.chellavignesh.authserver.token.dto.CreateTokenDto;
import com.chellavignesh.authserver.token.entity.Token;
import com.chellavignesh.authserver.token.entity.TokenRowMapper;
import com.chellavignesh.authserver.token.exception.TokenCreationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Types;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TokenRepository {

    private static final Logger log = LoggerFactory.getLogger(TokenRepository.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ApplicationService applicationService;

    private static final ThreadLocal<MessageDigest> SHA256_DIGEST_POOL =
            ThreadLocal.withInitial(() -> {
                try {
                    return MessageDigest.getInstance("SHA256");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

    @Autowired
    public TokenRepository(NamedParameterJdbcTemplate jdbcTemplate, ApplicationService applicationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.applicationService = applicationService;
    }

    /**
     * Creates a token and computes SHA256 hash of the payload for consistency.
     */
    public Token create(CreateTokenDto dto) throws TokenCreationFailedException {
        MessageDigest messageDigest = SHA256_DIGEST_POOL.get();
        messageDigest.reset();

        byte[] hashedData = messageDigest.digest(dto.getData().getBytes(StandardCharsets.UTF_8));

        log.debug("Generated SHA256 hash for token creation: {} bytes", hashedData.length);

        var parameters = new MapSqlParameterSource()
                .addValue("tokenTypeId", dto.getTokenType().getValue())
                .addValue("applicationId", dto.getApplicationId())
                .addValue("subjectId", dto.getSubjectId())
                .addValue("sessionId", dto.getSessionId())
                .addValue("isOpaque", dto.isOpaque())
                .addValue("data", dto.getData())
                .addValue("timeToLive", dto.getTimeToLive())
                .addValue("dataHash", hashedData);

        if (dto.getSigningKey() != null) {
            parameters.addValue("signingKey", dto.getSigningKey().getEncoded());
        } else {
            parameters.addValue("signingKey", null, Types.NULL);
        }

        Optional<Integer> tokenId = jdbcTemplate.query(
                """
                        {call Token.CreateTokenWithHash(
                            :tokenTypeId, :applicationId, :subjectId, :sessionId,
                            :isOpaque, :data, :signingKey, :timeToLive, :dataHash)}
                        """,
                parameters,
                (rs, rowNum) -> rs.getInt("ID")
        ).stream().findFirst();

        if (tokenId.isPresent()) {
            return getById(tokenId.get()).get();
        } else {
            throw new TokenCreationFailedException("Could not fetch newly created Token by ID.");
        }
    }

    public Optional<Token> getById(Integer id) {
        return jdbcTemplate.query(
                "{call Token.GetTokenById(:id)}",
                new MapSqlParameterSource("id", id),
                new TokenRowMapper()
        ).stream().findFirst();
    }

    public Optional<Token> getByClientId(String clientId) {
        var application = applicationService.getByClientId(clientId);
        return application.flatMap(value -> jdbcTemplate.query(
                "{call Token.GetTokenByClientId(:clientId)}",
                new MapSqlParameterSource("clientId", value.getClientId()),
                new TokenRowMapper()
        ).stream().findFirst());
    }

    @Cacheable(cacheNames = "token-get-by-value-hash", key = "#type.name() + ':' + #value")
    public Optional<Token> getByValue(String value, TokenTypeEnum type) {
        MessageDigest messageDigest = SHA256_DIGEST_POOL.get();
        messageDigest.reset();

        byte[] hashed = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));

        log.debug("[TOKEN-CACHE-MISS] querying database for token hash: {} bytes, type: {}", hashed.length, type);

        return jdbcTemplate.query(
                "{call Token.GetTokenByValueHash(:hashValue, :typeId)}",
                new MapSqlParameterSource()
                        .addValue("hashValue", hashed)
                        .addValue("typeId", type.getValue()),
                new TokenRowMapper()
        ).stream().findFirst();
    }

    public List<Token> getAllActiveBySessionId(UUID sessionId) {
        return jdbcTemplate.query(
                "{call Token.GetAllActiveTokensBySessionId(:sessionId)}",
                new MapSqlParameterSource("sessionId", sessionId.toString()),
                new TokenRowMapper()
        ).stream().toList();
    }

    public List<Token> getTokensByClientIdAndRequestDateTime(final String clientId, final Date requestDateTime) {
        return jdbcTemplate.query(
                "{call Token.GetTokensByClientIdAndRequestDateTime(:clientId, :requestDateTime)}",
                new MapSqlParameterSource()
                        .addValue("clientId", clientId)
                        .addValue("requestDateTime", requestDateTime),
                new TokenRowMapper()
        ).stream().toList();
    }
}
