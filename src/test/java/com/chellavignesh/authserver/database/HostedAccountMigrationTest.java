package com.chellavignesh.authserver.database;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.MessageDigest;
import java.util.*;

@EnabledIfEnvironmentVariable(named = "AUTH_DB_IT_URL", matches = ".+")
class HostedAccountMigrationTest {
    SQLServerDataSource source(String url) {
        var ds = new SQLServerDataSource();
        ds.setURL(url);
        ds.setUser(System.getenv("AUTH_DB_IT_USER"));
        ds.setPassword(System.getenv("AUTH_DB_IT_PASSWORD"));
        return ds;
    }

    byte[] snapshot(JdbcTemplate sql) throws Exception {
        var rows = new TreeMap<String, Object>();
        for (String table :
                List.of(
                        "Person.Profile",
                        "Person.Credential",
                        "Person.CredentialAudit",
                        "Partner.Organization",
                        "Partner.OrganizationGroup",
                        "Partner.OrganizationGroupPermission",
                        "Client.Application",
                        "Client.Secret",
                        "Partner.Certificate"))
            rows.put(table, sql.queryForList("SELECT * FROM " + table));
        return MessageDigest.getInstance("SHA-256")
                .digest(new ObjectMapper().findAndRegisterModules().writeValueAsBytes(rows));
    }

    @Test
    void populatedFourteenUpgradeAddsOnlyScopedProcedureAndPreservesRows() throws Exception {
        String url = System.getenv("AUTH_DB_IT_URL");
        assertThat(url)
                .matches(
                        "jdbc:sqlserver://(?:127\\.0\\.0\\.1|localhost):15433;encrypt=true;trustServerCertificate=true");
        String name = "AuthserverHostedUpgrade_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(source(url));
        admin.execute("CREATE DATABASE [" + name + "]");
        try {
            var ds = source(url + ";databaseName=" + name);
            var sql = new JdbcTemplate(ds);
            assertThat(
                            Flyway.configure()
                                    .dataSource(ds)
                                    .target("14")
                                    .load()
                                    .migrate()
                                    .migrationsExecuted)
                    .isEqualTo(14);
            int profile =
                    sql.queryForObject(
                            "INSERT"
                                + " Person.Profile(LoginProviderId,Email,EmailConfirmed,PhoneNumber,PhoneNumberConfirmed,TwoFactorEnabled,FirstName,DataOriginId)"
                                + " OUTPUT INSERTED.ProfileId"
                                + " VALUES(1,'hosted-upgrade@example.test',1,'+19999999998',1,0,'Preserved',0)",
                            Integer.class);
            int credential =
                    sql.queryForObject(
                            "INSERT"
                                + " Person.Credential(ProfileId,UserName,Password,Version,CredentialLocked,AccessFailedCount,DataOriginId)"
                                + " OUTPUT INSERTED.Id"
                                + " VALUES(?,'hosted-upgrade',0xAABBCC,12,0,0,0)",
                            Integer.class,
                            profile);
            sql.update(
                    "INSERT Person.CredentialAudit(CredentialId,Password,Version)"
                        + " VALUES(?,0xAABBCC,12)",
                    credential);
            byte[] before = snapshot(sql);
            var flyway = Flyway.configure().dataSource(ds).target("15").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(Arrays.equals(before, snapshot(sql))).isTrue();
            assertThat(
                            sql.queryForObject(
                                    "SELECT COUNT(*) FROM sys.procedures WHERE"
                                        + " object_id=OBJECT_ID('Person.UpdateHostedAccountPassword')",
                                    Integer.class))
                    .isEqualTo(1);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            flyway.validate();
        } finally {
            admin.execute("ALTER DATABASE [" + name + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
            admin.execute("DROP DATABASE [" + name + "]");
        }
    }
}
