package com.chellavignesh.authserver.session.sso;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

@Repository
@Slf4j
public class SingleSignOnRepository {

    private static final SingleSignOnCookieRowMapper COOKIE_ROW_MAPPER = new SingleSignOnCookieRowMapper();

    // PERFORMANCE: ThreadLocal MessageDigest pool for thread-safe, efficient hashing
    private static final ThreadLocal<MessageDigest> SHA256_DIGEST_POOL =
            ThreadLocal.withInitial(() -> {
                try {
                    return MessageDigest.getInstance("SHA-256");
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException("SHA-256 algorithm not available", e);
                }
            });

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public SingleSignOnRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public void insertCookie(SingleSignOnCookie cookie) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("hashedEncryptedSessionId", hashEncryptedSessionId(cookie.encryptedSessionId()));
        parameters.addValue("sessionId", cookie.sessionId());
        parameters.addValue("encryptedSessionId", cookie.encryptedSessionId());
        parameters.addValue("encryptionKey", cookie.encryptionKey());

        namedParameterJdbcTemplate.update("{call Token.InsertSsoCookie(:hashedEncryptedSessionId, :sessionId, :encryptedSessionId, :encryptionKey)}", parameters);
    }

    public Optional<SingleSignOnCookie> findCookieByEncryptedSessionId(byte[] encryptedSessionId) {
        try {
            var parameters = new MapSqlParameterSource();
            parameters.addValue("hashedEncryptedSessionId", hashEncryptedSessionId(encryptedSessionId));
            List<SingleSignOnCookie> result = namedParameterJdbcTemplate.query("{call Token.FindSsoCookieByEncryptedSessionId(:hashedEncryptedSessionId)}", parameters, COOKIE_ROW_MAPPER);
            return result.stream().filter(java.util.Objects::nonNull).findFirst();
        } catch (DataAccessException e) {
            log.error("Unable to retrieve session from Database", e);
            return Optional.empty();
        }
    }

    private byte[] hashEncryptedSessionId(byte[] encryptedSessionId) {
        try {
            MessageDigest messageDigest = SHA256_DIGEST_POOL.get();
            messageDigest.reset(); // Ensure clean state
            return messageDigest.digest(encryptedSessionId);
        } catch (Exception e) {
            log.error("Unable to get digest algorithm SHA-256", e);
            throw new RuntimeException(e);
        }
    }
}
