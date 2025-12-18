package com.chellavignesh.authserver.session;

import com.chellavignesh.authserver.session.dto.CreateAuthSessionDto;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.session.entity.AuthSessionRowMapper;
import com.chellavignesh.authserver.session.exception.AuthSessionCreationFailedException;
import com.chellavignesh.authserver.session.exception.FailedToUpdateSessionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuthSessionRepository {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionRepository.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AuthSessionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AuthSession create(CreateAuthSessionDto dto) throws AuthSessionCreationFailedException {

        var parameters = new MapSqlParameterSource().addValue("applicationId", dto.getApplicationId()).addValue("subjectId", dto.getSubjectId()).addValue("scope", dto.getScope()).addValue("authFlowId", dto.getAuthFlow().getValue()).addValue("clientId", dto.getClientId()).addValue("branding", dto.getBranding());

        if (dto.getClientFingerprint() != null) {
            parameters.addValue("clientFingerprint", dto.getClientFingerprint());
        } else {
            parameters.addValue("clientFingerprint", null, Types.NULL);
        }

        Optional<Integer> sessionId = jdbcTemplate.query("{call Token.CreateAuthSession(:applicationId, :subjectId, :scope, :authFlowId, :clientFingerprint, :clientId, :branding)}", parameters, (rs, rowNum) -> rs.getInt("ID")).stream().findFirst();

        if (sessionId.isPresent()) {
            return getById(sessionId.get()).get();
        }

        throw new AuthSessionCreationFailedException("Could not fetch newly created AuthSession by ID.");
    }

    public Optional<AuthSession> getById(Integer id) {
        return jdbcTemplate.query("{call Token.GetAuthSessionById(:id)}", new MapSqlParameterSource("id", id), new AuthSessionRowMapper()).stream().findFirst();
    }

    @Cacheable("session-get-by-session-id")
    public Optional<AuthSession> getBySessionId(UUID sessionId) {
        return jdbcTemplate.query("{call Token.GetAuthSessionBySessionId(:sessionId)}", new MapSqlParameterSource("sessionId", sessionId.toString()), new AuthSessionRowMapper()).stream().findFirst();
    }

    /**
     * Set session inactive – MUST invalidate session cache
     */
    @CacheEvict(cacheNames = "session-get-by-session-id", key = "#sessionId")
    public void setSessionInactive(UUID sessionId) throws FailedToUpdateSessionException {

        try {
            jdbcTemplate.update("{call Token.SetAuthSessionInactive(:sessionId)}", new MapSqlParameterSource("sessionId", sessionId.toString()));

            log.info("Invalidated session cache after setting session inactive, sessionId: {}", sessionId);
        } catch (DataAccessException e) {
            throw new FailedToUpdateSessionException("Something went wrong while updating the session in the database", e);
        }
    }

    public Optional<AuthSession> getMostRecentActiveSession(String principal) {
        return jdbcTemplate.query("{call Token.GetMostRecentActiveSession(:principal)}", new MapSqlParameterSource("principal", principal), new AuthSessionRowMapper()).stream().findFirst();
    }

    /**
     * Set session branding and redirect URI – MUST invalidate session cache
     */
    @CacheEvict(cacheNames = "session-get-by-session-id", key = "#sessionId")
    public void setBrandingAndRedirectUri(UUID sessionId, String branding, String redirectUri, Integer applicationId) throws FailedToUpdateSessionException {

        log.debug("Setting branding and redirect URI - sessionId: {}, applicationId: {}", sessionId, applicationId);

        var parameters = new MapSqlParameterSource().addValue("sessionId", sessionId.toString()).addValue("branding", branding).addValue("redirectUri", redirectUri).addValue("applicationId", applicationId);

        try {
            int rowsUpdated = jdbcTemplate.update("{call Token.SetBrandingAndRedirectUri(:sessionId, :branding, :redirectUri, :applicationId)}", parameters);

            log.debug("SetBrandingAndRedirectUri completed - rowsUpdated: {}", rowsUpdated);
        } catch (DataAccessException e) {
            throw new FailedToUpdateSessionException("Something went wrong while updating the session in the database", e);
        }
    }
}
