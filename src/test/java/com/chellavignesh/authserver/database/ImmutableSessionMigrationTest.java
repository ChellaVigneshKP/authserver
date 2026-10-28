package com.chellavignesh.authserver.database;

import static org.junit.jupiter.api.Assertions.*;

import com.chellavignesh.authserver.adminportal.application.ApplicationRepository;
import com.chellavignesh.authserver.adminportal.application.dto.CreateApplicationDto;
import com.chellavignesh.authserver.adminportal.application.dto.UpdateTokenSettingsDto;
import com.chellavignesh.authserver.adminportal.forgotusername.ForgotUsernameSetting;
import com.chellavignesh.authserver.adminportal.util.SecurityUtil;
import com.chellavignesh.authserver.enums.EnumRepository;
import com.chellavignesh.authserver.enums.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.security.MessageDigest;
import java.util.*;

@EnabledIfEnvironmentVariable(named = "AUTH_DB_IT_URL", matches = ".+")
class ImmutableSessionMigrationTest {
    byte[] digest(Object rows) throws Exception {
        return MessageDigest.getInstance("SHA-256")
                .digest(new ObjectMapper().findAndRegisterModules().writeValueAsBytes(rows));
    }

    @Test
    void populatedSixteenUpgradePreservesRowsLeavesHistoricalSessionsUnboundAndRepeatsSafely() throws Exception {
        var helper = new HostedAccountMigrationTest();
        String url = System.getenv("AUTH_DB_IT_URL");
        assertTrue(
                url.matches(
                        "jdbc:sqlserver://(?:127\\.0\\.0\\.1|localhost):15433;encrypt=true;trustServerCertificate=true"));
        String database = "AuthserverBindingUpgrade_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(helper.source(url));
        admin.execute("CREATE DATABASE [" + database + "]");
        try {
            var ds = helper.source(url + ";databaseName=" + database);
            var sql = new JdbcTemplate(ds);
            assertEquals(16, Flyway.configure().dataSource(ds).target("16").load().migrate().migrationsExecuted);
            var named = new NamedParameterJdbcTemplate(ds);
            new EnumRepository(named).loadEnumValues();
            var applications = new ApplicationRepository(named, new SecurityUtil());
            ReflectionTestUtils.setField(applications, "serverBasePath", "http://localhost:8080/services");
            var application = new CreateApplicationDto();
            application.setName("Preserved binding");
            application.setDescription("Owned migration fixture");
            application.setType(ApplicationTypeEnum.WEB);
            application.setAuthMethod(AuthFlowEnum.PKCE);
            application.setUsernameType(UsernameTypeEnum.USERNAME);
            application.setAllowForgotUsername(false);
            var settings = new UpdateTokenSettingsDto();
            settings.setAuthCodeTimeToLive(120);
            settings.setAccessTokenTimeToLive(600);
            settings.setRefreshTokenTimeToLive(3600);
            settings.setDeviceCodeTimeToLive(300);
            settings.setMaxRequestTransitTime(30);
            settings.setReuseRefreshTokens(false);
            var app =
                    applications.create(
                            1,
                            application,
                            settings,
                            new ForgotUsernameSetting(List.of()),
                            AccessTokenFormatEnum.REFERENCE);
            int profile =
                    sql.queryForObject(
                            "INSERT Person.Profile(LoginProviderId,Email,EmailConfirmed,PhoneNumber,PhoneNumberConfirmed,TwoFactorEnabled,FirstName,DataOriginId) OUTPUT INSERTED.ProfileId VALUES(1,'binding@example.test',1,'+19999999998',1,0,'Preserved',0)",
                            Integer.class);
            UUID login = UUID.randomUUID();
            sql.update(
                    "INSERT Person.Credential(ProfileId,UserName,Password,Version,CredentialLocked,AccessFailedCount,DataOriginId,RowGuid) VALUES(?,'binding-upgrade',0xAABBCC,12,0,0,0,?)",
                    profile,
                    login);
            int credential =
                    sql.queryForObject(
                            "SELECT Id FROM Person.Credential WHERE RowGuid=?", Integer.class, login);
            sql.update(
                    "INSERT Person.CredentialAudit(CredentialId,Password,Version) VALUES(?,0xAABBCC,12)",
                    credential);
            for (String subject : List.of("binding-upgrade", app.getClientId()))
                sql.queryForObject(
                        "EXEC Token.CreateAuthSession ?,?,?,?,?,?,?",
                        Integer.class,
                        app.getId(),
                        subject,
                        "openid",
                        app.getAuthFlow().getValue(),
                        new byte[] {1, 2},
                        app.getClientId(),
                        "idp");
            String oldColumns =
                    String.join(
                            ",",
                            sql.queryForList(
                                    "SELECT QUOTENAME(name) FROM sys.columns WHERE object_id=OBJECT_ID('Token.AuthSession') ORDER BY column_id",
                                    String.class));
            byte[] businessBefore = helper.snapshot(sql);
            byte[] sessionsBefore =
                    digest(
                            sql.queryForList(
                                    "SELECT " + oldColumns + " FROM Token.AuthSession ORDER BY AuthSessionId"));
            var flyway = Flyway.configure().dataSource(ds).load();
            assertEquals(2, flyway.migrate().migrationsExecuted);
            assertTrue(
                    Arrays.equals(businessBefore, helper.snapshot(sql)),
                    "V17/V18 must preserve existing profiles, credentials, clients and certificates");
            assertTrue(
                    Arrays.equals(
                            sessionsBefore,
                            digest(
                                    sql.queryForList(
                                            "SELECT "
                                                    + oldColumns
                                                    + " FROM Token.AuthSession ORDER BY AuthSessionId"))),
                    "V17/V18 must preserve existing session columns");
            assertEquals(
                    2,
                    sql.queryForObject("SELECT COUNT(*) FROM Token.AuthSession WHERE LoginId IS NULL", Integer.class),
                    "Historical sessions must stay unbound instead of being guessed from mutable usernames");
            assertEquals(0, flyway.migrate().migrationsExecuted);
            flyway.validate();
            int created =
                    sql.queryForObject(
                            "EXEC Token.CreateAuthSession ?,?,?,?,?,?,?,?",
                            Integer.class,
                            app.getId(),
                            "binding-upgrade",
                            "openid",
                            app.getAuthFlow().getValue(),
                            new byte[] {1, 2},
                            app.getClientId(),
                            "idp",
                            login);
            assertTrue(
                    login.toString()
                            .equalsIgnoreCase(
                                    sql.queryForObject(
                                            "SELECT CONVERT(VARCHAR(36),LoginId) FROM Token.AuthSession WHERE AuthSessionId=?",
                                            String.class,
                                            created)));
            assertEquals(
                    2,
                    sql.queryForObject("SELECT COUNT(*) FROM Token.AuthSession WHERE LoginId IS NULL", Integer.class),
                    "Only sessions created with an explicit login may be bound");
        } finally {
            admin.execute("ALTER DATABASE [" + database + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
            admin.execute("DROP DATABASE [" + database + "]");
        }
    }
}
