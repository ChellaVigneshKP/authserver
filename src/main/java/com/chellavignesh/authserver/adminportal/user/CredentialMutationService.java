package com.chellavignesh.authserver.adminportal.user;

import com.chellavignesh.authserver.adminportal.globalconfig.GlobalConfigCache;
import com.chellavignesh.authserver.adminportal.hosted.*;
import com.chellavignesh.authserver.adminportal.organization.role.RoleFailure;
import com.chellavignesh.authserver.adminportal.user.entity.User;
import com.chellavignesh.authserver.enums.entity.GlobalConfigTypeEnum;
import com.chellavignesh.authserver.session.CredentialAuthenticationLock;
import com.chellavignesh.authserver.session.PasswordEncoderFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** All legacy profile-based password writers resolve one credential before writing anything. */
@Service
public class CredentialMutationService {
    private final NamedParameterJdbcTemplate sql;
    private final CredentialAuthenticationLock locks;
    private final HostedAccountPasswordRepository passwords;
    private final HostedAccountRevocationRepository revocations;
    private final PasswordEncoderFactory encoder;
    private final GlobalConfigCache config;
    private final UserRepository users;

    public CredentialMutationService(NamedParameterJdbcTemplate sql, CredentialAuthenticationLock locks,
            HostedAccountPasswordRepository passwords, HostedAccountRevocationRepository revocations,
            PasswordEncoderFactory encoder, GlobalConfigCache config, UserRepository users) {
        this.sql=sql; this.locks=locks; this.passwords=passwords; this.revocations=revocations;
        this.encoder=encoder; this.config=config; this.users=users;
    }

    public UUID resolve(UUID profile, String branding, UUID exactLogin) {
        return resolve(profile, branding, exactLogin, true);
    }

    /** Authorized management writes resolve an inactive target too; the controller verified the actor. */
    public UUID resolveForManagement(UUID profile, String branding, UUID exactLogin) {
        return resolve(profile, branding, exactLogin, false);
    }

    private UUID resolve(UUID profile, String branding, UUID exactLogin, boolean requireActiveProfile) {
        var params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("profile",profile.toString()).addValue("branding",branding)
                .addValue("login",exactLogin == null ? null : exactLogin.toString());
        var ids=sql.query("""
                SELECT c.RowGuid FROM Person.Credential c
                JOIN Person.Profile p ON p.ProfileId=c.ProfileId
                JOIN dbo.ExternalSource e ON e.SourceId=c.ExternalId
                WHERE p.RowGuid=:profile AND (:branding IS NULL OR e.SourceCode=:branding)
                  AND (:login IS NULL OR c.RowGuid=:login) AND c.Status=1"""
                        + (requireActiveProfile ? " AND p.Status=1" : ""),
                params,(rs,n)->UUID.fromString(rs.getString(1)));
        if(ids.size()!=1) throw RoleFailure.credential();
        if(!Integer.valueOf(1).equals(sql.queryForObject("SELECT COUNT(*) FROM Person.Credential WHERE RowGuid=:login",Map.of("login",ids.getFirst().toString()),Integer.class)))throw RoleFailure.credential();
        return ids.getFirst();
    }

    public UUID lock(UUID profile,String branding,UUID selected) {
        UUID login=resolveForManagement(profile,branding,selected);locks.acquire(login);
        if(!login.equals(resolveForManagement(profile,branding,selected)))throw RoleFailure.credential();
        return login;
    }

    @Transactional(rollbackFor=Exception.class)
    public User password(UUID profile, String branding, UUID exactLogin, String value, boolean unlock, UUID actualActor) {
        UUID login=resolveForManagement(profile,branding,exactLogin);
        locks.acquire(login);
        if(!login.equals(resolveForManagement(profile,branding,exactLogin))) throw new RoleFailure(409);
        var current=users.getManagementCredentialByLoginId(login).orElseThrow();
        if (unlock) sql.update("UPDATE Person.Credential SET CredentialLocked=0,AccessFailedCount=0 WHERE RowGuid=:login",
                Map.of("login",login.toString()));
        var actor=new HostedAccountActor(null,login,profile,current.branding(),null,null,current.toUserDetails());
        passwords.currentForManagement(actor);
        Integer limit=config.getGlobalConfig(GlobalConfigTypeEnum.DISALLOWED_RECENT_PASSWORD_COUNT);
        if(limit==null) throw new IllegalStateException("Password history configuration unavailable");
        byte[] encoded=HexFormat.of().parseHex(encoder.encode(Base64.getEncoder().encodeToString(
                value.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        passwords.update(actor,encoded,PasswordEncoderFactory.currentVersion,limit,actualActor);
        revocations.revokeAllForLogin(login);
        return current.toUser();
    }
    @Transactional(rollbackFor=Exception.class)
    public User rename(UUID profile, String branding, UUID selected, String username, UUID actualActor) {
        UUID login=lock(profile,branding,selected);
        var current=users.getManagementCredentialByLoginId(login).orElseThrow();
        revocations.revokeAllForLogin(login);
        int changed=sql.update("""
            UPDATE Person.Credential SET UserName=:username,ModifiedOn=SYSUTCDATETIME(),ModifiedBy=:actor
            WHERE RowGuid=:login AND ExternalId=(SELECT SourceId FROM dbo.ExternalSource WHERE SourceCode=:branding)
            """,Map.of("username",username,"actor",Objects.requireNonNull(actualActor).toString(),"login",login.toString(),"branding",branding));
        if(changed!=1) throw RoleFailure.credential();
        return current.toUser();
    }

}
