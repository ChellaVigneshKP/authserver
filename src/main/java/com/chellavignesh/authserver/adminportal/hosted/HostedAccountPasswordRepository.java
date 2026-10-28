package com.chellavignesh.authserver.adminportal.hosted;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.*;

/** The legacy profile/source writer is intentionally not used by additive hosted forms. */
@Repository
public class HostedAccountPasswordRepository {
    public record Credential(
            int id,
            UUID loginId,
            UUID profileId,
            String username,
            String branding,
            byte[] password,
            int version) {}

    public record History(byte[] password, int version) {}

    private final NamedParameterJdbcTemplate sql;

    public HostedAccountPasswordRepository(NamedParameterJdbcTemplate sql) {
        this.sql = sql;
    }

    private static Map<String, Object> parameters(HostedAccountActor actor) {
        return Map.of(
                "login",
                actor.loginId().toString(),
                "member",
                actor.profileId().toString(),
                "source",
                actor.branding());
    }

    public Credential current(HostedAccountActor actor) {
        return current(actor, true);
    }

    /** Authorized management writes keep an inactive profile eligible; the caller verified the actor. */
    public Credential currentForManagement(HostedAccountActor actor) {
        return current(actor, false);
    }

    private Credential current(HostedAccountActor actor, boolean requireActiveProfile) {
        var rows =
                sql.query(
                        """
                        SELECT c.Id,c.RowGuid AS LoginId,p.RowGuid AS ProfileId,c.UserName,source.SourceCode,c.Password,c.Version
                        FROM Person.Credential c WITH (UPDLOCK,HOLDLOCK)
                        JOIN Person.Profile p ON p.ProfileId=c.ProfileId JOIN dbo.ExternalSource source ON source.SourceId=c.ExternalId
                        WHERE c.RowGuid=:login AND p.RowGuid=:member AND source.SourceCode=:source
                          AND c.Status=1 AND c.CredentialLocked=0"""
                                + (requireActiveProfile ? " AND p.Status=1" : ""),
                        parameters(actor),
                        (rs, n) ->
                                new Credential(
                                        rs.getInt("Id"),
                                        UUID.fromString(rs.getString("LoginId")),
                                        UUID.fromString(rs.getString("ProfileId")),
                                        rs.getString("UserName"),
                                        rs.getString("SourceCode"),
                                        rs.getBytes("Password"),
                                        rs.getInt("Version")));
        if (rows.size() != 1) throw HostedAccountFailure.denied();
        return rows.getFirst();
    }

    public List<History> history(HostedAccountActor actor) {
        return sql.query(
                """
                SELECT history.Password,history.Version FROM Person.CredentialAudit history
                JOIN Person.Credential c ON c.Id=history.CredentialId
                JOIN Person.Profile p ON p.ProfileId=c.ProfileId JOIN dbo.ExternalSource source ON source.SourceId=c.ExternalId
                WHERE c.RowGuid=:login AND p.RowGuid=:member AND source.SourceCode=:source
                """,
                parameters(actor),
                (rs, n) -> new History(rs.getBytes("Password"), rs.getInt("Version")));
    }

    public void update(HostedAccountActor actor, byte[] encoded, int version, int auditLimit) {
        update(actor,encoded,version,auditLimit,actor.profileId());
    }

    public void update(HostedAccountActor actor, byte[] encoded, int version, int auditLimit, UUID actualActor) {
        java.util.Objects.requireNonNull(actualActor,"Password audit actor required");
        var parameters =
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(
                                "login", actor.loginId().toString())
                        .addValue("member", actor.profileId().toString())
                        .addValue("source", actor.branding())
                        .addValue("password", encoded, Types.VARBINARY)
                        .addValue("version", version)
                        .addValue("limit", auditLimit).addValue("actor",actualActor.toString());
        var ids =
                sql.query(
                        "{call"
                            + " Person.UpdateHostedAccountPassword(:login,:member,:source,:password,:version,:limit,:actor)}",
                        parameters,
                        (rs, n) -> rs.getInt("CredentialId"));
        if (ids.size() != 1) throw HostedAccountFailure.denied();
    }
}
