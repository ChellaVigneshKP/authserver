package com.chellavignesh.authserver.adminportal.hosted;

import com.chellavignesh.authserver.config.JdbcOAuth2AuthorizationService;
import com.chellavignesh.authserver.enums.entity.AuthSessionStatusEnum;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class HostedAccountRevocationRepository {
    private final NamedParameterJdbcTemplate sql;

    public HostedAccountRevocationRepository(NamedParameterJdbcTemplate sql) {
        this.sql = sql;
    }

    public void revokeAllForLogin(UUID loginId) {
        var targets =
                sql.query(
                        """
                        SELECT c.UserName,source.SourceCode FROM Person.Credential c WITH (UPDLOCK,HOLDLOCK)
                        JOIN dbo.ExternalSource source ON source.SourceId=c.ExternalId WHERE c.RowGuid=:login
                        """,
                        Map.of("login", loginId.toString()),
                        (rs, n) ->
                                Map.of(
                                        "username",
                                        rs.getString("UserName"),
                                        "source",
                                        rs.getString("SourceCode")));
        if (targets.size() != 1) throw HostedAccountFailure.denied();
        var parameters = new HashMap<String, Object>(targets.getFirst());
        parameters.put("login", loginId.toString());
        parameters.put("inactive", AuthSessionStatusEnum.INACTIVE.getValue());
        // Interactive/recovery sessions have canonical branding; machine sessions do not.
        // Spring grants additionally require non-machine type and exact session/client/principal
        // correlation.
        String bindingPath = "$.\"" + JdbcOAuth2AuthorizationService.SESSION_ID + "\"";
        parameters.put("bindingPath", bindingPath);
        sql.update(
                """
                DELETE grantState FROM dbo.oauth2_authorization grantState
                JOIN Token.AuthSession auth ON TRY_CONVERT(UNIQUEIDENTIFIER,
                  JSON_VALUE(CASE WHEN ISJSON(grantState.attributes)=1 THEN grantState.attributes ELSE N'{}' END,:bindingPath))=auth.SessionId
                JOIN Client.Application client ON client.ApplicationId=auth.ApplicationId
                WHERE auth.LoginId=:login
                  AND grantState.principal_name=auth.SubjectId AND grantState.registered_client_id=client.ClientId
                  AND grantState.authorization_grant_type<>'client_credentials'
                """,
                parameters);
        sql.update(
                "DELETE cookie FROM Token.SSO cookie JOIN Token.AuthSession auth ON"
                    + " auth.SessionId=cookie.SessionId WHERE auth.LoginId=:login",
                parameters);
        sql.update(
                "UPDATE token SET ConsumedOn=COALESCE(token.ConsumedOn,SYSUTCDATETIME()) FROM"
                    + " Token.Token token JOIN Token.AuthSession auth ON"
                    + " auth.SessionId=token.SessionId WHERE auth.LoginId=:login",
                parameters);
        sql.update(
                "UPDATE code SET ConsumedOn=COALESCE(code.ConsumedOn,SYSUTCDATETIME()) FROM"
                    + " Token.AuthCode code JOIN Token.AuthSession auth ON"
                    + " auth.SessionId=code.SessionId WHERE auth.LoginId=:login",
                parameters);
        sql.update(
                "UPDATE proof SET ConsumedOn=COALESCE(proof.ConsumedOn,SYSUTCDATETIME()) FROM"
                    + " Token.Pkce proof JOIN Token.AuthSession auth ON"
                    + " auth.SessionId=proof.SessionId WHERE auth.LoginId=:login",
                parameters);
        sql.update(
                "UPDATE Token.AuthSession SET"
                    + " AuthSessionStatusId=:inactive,ModifiedOn=SYSUTCDATETIME() WHERE"
                    + " LoginId=:login",
                parameters);
    }
}
