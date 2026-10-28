package com.chellavignesh.authserver.session;

import com.chellavignesh.authserver.session.dto.CreateAuthSessionDto;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.session.entity.AuthSessionRowMapper;
import com.chellavignesh.authserver.session.exception.AuthSessionCreationFailedException;
import com.chellavignesh.authserver.session.exception.FailedToUpdateSessionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;

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

        var parameters = new MapSqlParameterSource().addValue("applicationId", dto.getApplicationId()).addValue("subjectId", dto.getSubjectId()).addValue("scope", dto.getScope()).addValue("authFlowId", dto.getAuthFlow().getValue()).addValue("clientId", dto.getClientId()).addValue("branding", dto.getBranding()).addValue("loginId", dto.getLoginId() == null ? null : dto.getLoginId().toString());

        if (dto.getClientFingerprint() != null) {
            parameters.addValue("clientFingerprint", dto.getClientFingerprint());
        } else {
            parameters.addValue("clientFingerprint", null, Types.VARBINARY);
        }

        Optional<Integer> sessionId = jdbcTemplate.query("{call Token.CreateAuthSession(:applicationId, :subjectId, :scope, :authFlowId, :clientFingerprint, :clientId, :branding, :loginId)}", parameters, (rs, rowNum) -> rs.getInt("ID")).stream().findFirst();

        if (sessionId.isPresent()) {
            return getById(sessionId.get()).orElseThrow(() -> new AuthSessionCreationFailedException("Created session is missing"));
        }

        throw new AuthSessionCreationFailedException("Could not fetch newly created AuthSession by ID.");
    }

    public Optional<AuthSession> getById(Integer id) {
        return jdbcTemplate.query("{call Token.GetAuthSessionById(:id)}", new MapSqlParameterSource("id", id), new AuthSessionRowMapper()).stream().findFirst();
    }

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
    public boolean isHumanIdentityCurrent(AuthSession session) {
        if(session.getLoginId()==null || session.getSubjectId()==null || session.getBranding()==null) return false;
        return Integer.valueOf(1).equals(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM Person.Credential c
            JOIN Person.Profile p ON p.ProfileId=c.ProfileId
            JOIN dbo.ExternalSource e ON e.SourceId=c.ExternalId
            WHERE c.RowGuid=:login AND c.UserName=:subject AND e.SourceCode=:branding
              AND c.Status=1 AND p.Status=1 AND c.CredentialLocked=0
            """, new MapSqlParameterSource("login",session.getLoginId().toString())
                    .addValue("subject",session.getSubjectId()).addValue("branding",session.getBranding()), Integer.class));
    }

    /** A null binding is never proof of a machine. Require the persisted, correlated grant. */
    public boolean isProvenMachine(AuthSession session) {
        if(session.getLoginId()!=null) return false;
        return Integer.valueOf(1).equals(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM dbo.oauth2_authorization grantState
            JOIN Token.AuthSession auth ON TRY_CONVERT(UNIQUEIDENTIFIER,
              JSON_VALUE(CASE WHEN ISJSON(grantState.attributes)=1 THEN grantState.attributes ELSE N'{}' END,:bindingPath))=auth.SessionId
            JOIN Client.Application client ON client.ApplicationId=auth.ApplicationId
            WHERE auth.SessionId=:session AND auth.LoginId IS NULL
              AND auth.SubjectId=client.ClientId
              AND grantState.principal_name=client.ClientId
              AND grantState.registered_client_id=client.ClientId
              AND grantState.authorization_grant_type='client_credentials'
            """,new MapSqlParameterSource("session",session.getSessionId().toString())
                .addValue("bindingPath","$.\""+com.chellavignesh.authserver.config.JdbcOAuth2AuthorizationService.SESSION_ID+"\""),Integer.class));
    }

}
