package com.chellavignesh.authserver.session.sso;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Slf4j
public class SingleSignOnCookieRowMapper implements RowMapper<SingleSignOnCookie> {

    private final Logger logger =
            LoggerFactory.getLogger(SingleSignOnCookieRowMapper.class);

    @Override
    public SingleSignOnCookie mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
        try {
            var sessionId = UUID.fromString(rs.getString("SessionID"));
            var encryptedSessionId = rs.getBytes("EncryptedSessionID");
            var encryptionKey = rs.getBytes("EncryptionKey");
            return new SingleSignOnCookie(sessionId, encryptedSessionId, encryptionKey);
        } catch (SQLException | IllegalArgumentException e) {
            logger.error("Unable to retrieve SSO cookie from database", e);
            return null;
        }
    }
}