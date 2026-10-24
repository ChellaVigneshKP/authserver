package com.chellavignesh.authserver.session;

import com.chellavignesh.authserver.session.dto.CustomUserDetails;
import com.chellavignesh.authserver.session.entity.AuthSession;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Human issuance and password revocation serialize per stable credential, never per machine grant.
 */
@Service
public class CredentialAuthenticationLock {
    private final NamedParameterJdbcTemplate sql;

    public CredentialAuthenticationLock(NamedParameterJdbcTemplate sql) {
        this.sql = sql;
    }

    public void acquire(UUID loginId) {
        if (loginId == null || !TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException(
                    "Credential authentication lock requires a transaction and login identity");
        try {
            sql.update(
                    """
                    DECLARE @bootstrap INT;
                    IF @@TRANCOUNT=0 SELECT @bootstrap=Id FROM Person.Credential WHERE 1=0;
                    DECLARE @result INT;
                    EXEC @result=sys.sp_getapplock @Resource=:resource,
                      @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
                    IF @result<0 THROW 51013, 'Credential authentication lock unavailable',1;
                    """,
                    Map.of("resource", "authserver:credential-auth:v1:" + loginId));
        } catch (UncategorizedSQLException failure) {
            if (failure.getSQLException() != null
                    && failure.getSQLException().getErrorCode() == 51013)
                throw new QueryTimeoutException(
                        "Credential authentication temporarily unavailable", failure);
            throw failure;
        }
    }

    /** A lookup before the lock chooses only its key; authority is re-read after acquiring it. */
    public void lockForHumanSession(AuthSession session) {
        var before = identity(session.getSubjectId(), session.getBranding());
        acquire(before.loginId());
        if (!before.loginId()
                .equals(identity(session.getSubjectId(), session.getBranding()).loginId()))
            throw stale();
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthSession createInteractiveSession(
            CustomUserDetails captured, String branding, Callable<AuthSession> create)
            throws Exception {
        if (captured == null
                || captured.getLoginId() == null
                || captured.getUserAuthDetails() == null) throw stale();
        acquire(captured.getLoginId());
        var fresh = identity(captured.getUsername(), branding);
        var snapshot = captured.getUserAuthDetails();
        boolean matches =
                fresh.loginId().equals(captured.getLoginId())
                        && fresh.profileId().equals(snapshot.getRowGuid())
                        && Objects.equals(fresh.version(), snapshot.getVersion())
                        && snapshot.getPassword() != null;
        if (matches) {
            try {
                matches =
                        MessageDigest.isEqual(
                                fresh.password(), HexFormat.of().parseHex(snapshot.getPassword()));
            } catch (IllegalArgumentException badSnapshot) {
                matches = false;
            }
        }
        if (!matches) throw stale();
        return create.call();
    }

    private record Identity(UUID loginId, UUID profileId, byte[] password, Integer version) {}

    private Identity identity(String username, String branding) {
        if (username == null || branding == null) throw stale();
        var rows =
                sql.query(
                        """
                        SELECT c.RowGuid AS LoginId,p.RowGuid AS ProfileId,c.Password,c.Version
                        FROM Person.Credential c JOIN Person.Profile p ON p.ProfileId=c.ProfileId
                        JOIN dbo.ExternalSource e ON e.SourceId=c.ExternalId
                        WHERE c.UserName=:username AND e.SourceCode=:branding
                          AND c.Status=1 AND c.CredentialLocked=0 AND p.Status=1
                        """,
                        Map.of("username", username, "branding", branding),
                        (rs, n) ->
                                new Identity(
                                        UUID.fromString(rs.getString("LoginId")),
                                        UUID.fromString(rs.getString("ProfileId")),
                                        rs.getBytes("Password"),
                                        rs.getInt("Version")));
        if (rows.size() != 1) throw stale();
        return rows.getFirst();
    }

    private static CredentialsExpiredException stale() {
        return new CredentialsExpiredException("The authenticated account changed. Sign in again.");
    }
}
