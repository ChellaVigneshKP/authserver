package com.chellavignesh.authserver.database;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="AUTH_DB_IT_URL", matches=".+")
class RoleMigrationContractTest {
    SQLServerDataSource source(String url) {
        var ds=new SQLServerDataSource();ds.setURL(url);ds.setUser(System.getenv("AUTH_DB_IT_USER"));ds.setPassword(System.getenv("AUTH_DB_IT_PASSWORD"));return ds;
    }
    @Test void populatedElevenMigrationUpgradePreservesIdsAndReportsAmbiguity() {
        String url=System.getenv("AUTH_DB_IT_URL");
        assertThat(url).matches("jdbc:sqlserver://(?:127\\.0\\.0\\.1|localhost):15433;encrypt=true;trustServerCertificate=true");
        String name="AuthserverRoleUpgrade_"+UUID.randomUUID().toString().replace("-","");
        var admin=new JdbcTemplate(source(url));admin.execute("CREATE DATABASE ["+name+"]");
        try {
            var ds=source(url+";databaseName="+name);var sql=new JdbcTemplate(ds);
            assertThat(Flyway.configure().dataSource(ds).target("11").load().migrate().migrationsExecuted).isEqualTo(11);
            var builtin=sql.queryForMap("SELECT RowGuid,GroupName,GroupTemplateId FROM Partner.OrganizationGroup WHERE OrganizationGroupId=1");
            var templateIds=sql.queryForList("SELECT GroupTemplateId,RowGuid FROM dbo.GroupTemplate ORDER BY GroupTemplateId");
            var permissionIds=sql.queryForList("SELECT OrganizationGroupPermissionId,RowGuid,PermissionKey FROM Partner.OrganizationGroupPermission ORDER BY OrganizationGroupPermissionId");
            var legacyDuplicates=sql.queryForList("SELECT OrganizationGroupId,PermissionKey,COUNT(*) GrantCount FROM Partner.OrganizationGroupPermission GROUP BY OrganizationGroupId,PermissionKey HAVING COUNT(*)>1");
            sql.update("INSERT Partner.OrganizationGroup(OrganizationId,GroupName) VALUES(1,'Legacy duplicate'),(1,'legacy duplicate')");
            int profile=sql.queryForObject("INSERT Person.Profile(LoginProviderId,Email,EmailConfirmed,PhoneNumber,PhoneNumberConfirmed,TwoFactorEnabled,FirstName,DataOriginId) OUTPUT INSERTED.ProfileId VALUES(1,'upgrade@example.test',1,'+19999999999',1,0,'Upgrade',0)",Integer.class);
            sql.update("INSERT Person.ProfileOrganization(ProfileId,OrganizationId) VALUES(?,1)",profile);
            sql.update("INSERT Person.ProfileGroup(ProfileId,OrganizationGroupId) VALUES(?,1),(?,2)",profile,profile);
            var members=sql.queryForList("SELECT * FROM Person.ProfileGroup ORDER BY ProfileGroupId");
            var profiles=sql.queryForList("SELECT * FROM Person.Profile ORDER BY ProfileId");
            var flyway=Flyway.configure().dataSource(ds).load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
            assertThat(sql.queryForMap("SELECT RowGuid,GroupName,GroupTemplateId FROM Partner.OrganizationGroup WHERE OrganizationGroupId=1")).isEqualTo(builtin);
            assertThat(sql.queryForList("SELECT GroupTemplateId,RowGuid FROM dbo.GroupTemplate ORDER BY GroupTemplateId")).isEqualTo(templateIds);
            assertThat(sql.queryForList("SELECT OrganizationGroupPermissionId,RowGuid,PermissionKey FROM Partner.OrganizationGroupPermission WHERE OrganizationGroupPermissionId<=? ORDER BY OrganizationGroupPermissionId",permissionIds.size())).isEqualTo(permissionIds);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM Partner.RolePreflightIssues WHERE Code='DUPLICATE_ROLE_NAME' AND OrganizationId=1",Integer.class)).isPositive();
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM Partner.OrganizationGroup WHERE LOWER(GroupName)='legacy duplicate'",Integer.class)).isEqualTo(2);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM Partner.RolePreflightIssues WHERE Code='AMBIGUOUS_MEMBERSHIP' AND ProfileId=?",Integer.class,profile)).isPositive();
            assertThat(sql.queryForList("SELECT * FROM Person.ProfileGroup ORDER BY ProfileGroupId")).isEqualTo(members);
            assertThat(sql.queryForList("SELECT * FROM Person.Profile ORDER BY ProfileId")).isEqualTo(profiles);
            int permissionRows=sql.queryForObject("SELECT COUNT(*) FROM Partner.OrganizationGroupPermission",Integer.class);
            int templates=sql.queryForObject("SELECT COUNT(*) FROM dbo.GroupPermissionTemplate",Integer.class);
            assertThat(flyway.migrate().migrationsExecuted).isZero();flyway.validate();
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM Partner.OrganizationGroupPermission",Integer.class)).isEqualTo(permissionRows);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM dbo.GroupPermissionTemplate",Integer.class)).isEqualTo(templates);
            assertThat(sql.queryForList("SELECT OrganizationGroupId,PermissionKey,COUNT(*) GrantCount FROM Partner.OrganizationGroupPermission GROUP BY OrganizationGroupId,PermissionKey HAVING COUNT(*)>1")).isEqualTo(legacyDuplicates);
            assertThat(sql.queryForObject("SELECT IsBuiltin FROM Partner.OrganizationGroup WHERE OrganizationGroupId=1",Boolean.class)).isTrue();
        } finally { admin.execute("ALTER DATABASE ["+name+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");admin.execute("DROP DATABASE ["+name+"]"); }
    }
    @Test void populatedTwelveUpgradePreservesCredentialsAndReportsMembershipProblems() {
        String url=System.getenv("AUTH_DB_IT_URL");
        assertThat(url).matches("jdbc:sqlserver://(?:127\\.0\\.0\\.1|localhost):15433;encrypt=true;trustServerCertificate=true");
        String name="AuthserverAssignmentUpgrade_"+UUID.randomUUID().toString().replace("-","");
        var admin=new JdbcTemplate(source(url));admin.execute("CREATE DATABASE ["+name+"]");
        try {
            var ds=source(url+";databaseName="+name);var sql=new JdbcTemplate(ds);
            assertThat(Flyway.configure().dataSource(ds).target("12").load().migrate().migrationsExecuted).isEqualTo(12);
            int profile=sql.queryForObject("INSERT Person.Profile(LoginProviderId,Email,EmailConfirmed,PhoneNumber,PhoneNumberConfirmed,TwoFactorEnabled,FirstName,DataOriginId) OUTPUT INSERTED.ProfileId VALUES(1,'upgrade@example.test',1,'+19999999999',1,0,'Upgrade',0)",Integer.class);
            sql.update("INSERT Person.Credential(ProfileId,UserName,Password,CredentialLocked,AccessFailedCount,DataOriginId) VALUES(?,'upgrade-actor',0x00112233,0,0,0)",profile);
            sql.update("INSERT Person.ProfileOrganization(ProfileId,OrganizationId) VALUES(?,1)",profile);
            sql.update("INSERT Person.ProfileGroup(ProfileId,OrganizationGroupId) VALUES(?,1)",profile);
            int ambiguous=sql.queryForObject("INSERT Person.Profile(LoginProviderId,Email,EmailConfirmed,PhoneNumber,PhoneNumberConfirmed,TwoFactorEnabled,FirstName,DataOriginId) OUTPUT INSERTED.ProfileId VALUES(1,'ambiguous@example.test',1,'+19999999999',1,0,'Ambiguous',0)",Integer.class);
            sql.update("INSERT Person.ProfileOrganization(ProfileId,OrganizationId) VALUES(?,1),(?,1)",ambiguous,ambiguous);
            var profiles=sql.queryForList("SELECT * FROM Person.Profile ORDER BY ProfileId");
            var credentials=sql.queryForList("SELECT * FROM Person.Credential ORDER BY Id");
            var memberships=sql.queryForList("SELECT * FROM Person.ProfileOrganization ORDER BY ProfileOrganizationId");
            var groups=sql.queryForList("SELECT * FROM Person.ProfileGroup ORDER BY ProfileGroupId");
            var permissions=sql.queryForList("SELECT * FROM Partner.OrganizationGroupPermission ORDER BY OrganizationGroupPermissionId");
            var flyway=Flyway.configure().dataSource(ds).load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
            assertThat(sql.queryForList("SELECT * FROM Person.Profile ORDER BY ProfileId")).isEqualTo(profiles);
            assertThat(sql.queryForList("SELECT * FROM Person.Credential ORDER BY Id")).usingRecursiveComparison().isEqualTo(credentials);
            assertThat(sql.queryForList("SELECT * FROM Person.ProfileOrganization ORDER BY ProfileOrganizationId")).isEqualTo(memberships);
            assertThat(sql.queryForList("SELECT * FROM Person.ProfileGroup ORDER BY ProfileGroupId")).isEqualTo(groups);
            assertThat(sql.queryForList("SELECT * FROM Partner.OrganizationGroupPermission ORDER BY OrganizationGroupPermissionId")).isEqualTo(permissions);
            assertThat(sql.queryForObject("SELECT COUNT(DISTINCT ProfileId) FROM Person.EffectivePlatformAdministrator",Integer.class)).isEqualTo(1);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.ManagementMembershipIssues WHERE ProfileId=?",Integer.class,ambiguous)).isEqualTo(1);
            assertThat(sql.queryForList("EXEC Person.GetUserPermissions ?",profile)).isNotEmpty();
            sql.update("UPDATE Partner.OrganizationGroup SET Status=0 WHERE OrganizationGroupId=1");
            assertThat(sql.queryForList("EXEC Person.GetUserPermissions ?",profile)).isEmpty();
            assertThat(sql.queryForObject("SELECT COUNT(DISTINCT ProfileId) FROM Person.EffectivePlatformAdministrator",Integer.class)).isZero();
            assertThat(flyway.migrate().migrationsExecuted).isZero();flyway.validate();
        } finally {admin.execute("ALTER DATABASE ["+name+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");admin.execute("DROP DATABASE ["+name+"]");}
    }

    @Test void populatedThirteenUpgradePreservesHistoryAndScopesCurrentList() {
        String url=System.getenv("AUTH_DB_IT_URL");
        assertThat(url).matches("jdbc:sqlserver://(?:127\\.0\\.0\\.1|localhost):15433;encrypt=true;trustServerCertificate=true");
        String name="AuthserverListUpgrade_"+UUID.randomUUID().toString().replace("-","");
        var admin=new JdbcTemplate(source(url));
        admin.execute("CREATE DATABASE ["+name+"]");
        try {
            var ds=source(url+";databaseName="+name);
            var sql=new JdbcTemplate(ds);
            assertThat(Flyway.configure().dataSource(ds).target("13").load().migrate().migrationsExecuted).isEqualTo(13);
            int profile=sql.queryForObject("INSERT Person.Profile(LoginProviderId,Email,EmailConfirmed,PhoneNumber,PhoneNumberConfirmed,TwoFactorEnabled,FirstName,DataOriginId) OUTPUT INSERTED.ProfileId VALUES(1,'list-upgrade@example.test',1,'+19999999999',1,0,'List upgrade',0)",Integer.class);
            sql.update("INSERT Person.Credential(ProfileId,UserName,Password,CredentialLocked,AccessFailedCount,DataOriginId,ExternalId) VALUES(?,'list-upgrade',0x00112233,0,0,0,'11111111-1111-1111-1111-111111111111')",profile);
            sql.update("INSERT Person.ProfileOrganization(ProfileId,OrganizationId,Status) VALUES(?,1,1),(?,1,0)",profile,profile);
            sql.update("INSERT Person.ProfileGroup(ProfileId,OrganizationGroupId) VALUES(?,1)",profile);
            var tables=List.of("Person.Profile","Person.Credential","Person.ProfileOrganization","Person.ProfileGroup","Partner.OrganizationGroup","Partner.OrganizationGroupPermission");
            var before=new LinkedHashMap<String,List<Map<String,Object>>>();
            for(String table:tables) before.put(table,sql.queryForList("SELECT * FROM "+table));
            assertThat(sql.queryForList("EXEC Person.GetUsers 100,0,NULL,N'list-upgrade',NULL,NULL,NULL")).hasSize(2);
            var flyway=Flyway.configure().dataSource(ds).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            for(String table:tables) assertThat(sql.queryForList("SELECT * FROM "+table)).usingRecursiveComparison().isEqualTo(before.get(table));
            var listed=sql.queryForList("EXEC Person.GetUsers 100,0,NULL,N'list-upgrade',N'Administrator',NULL,NULL");
            assertThat(listed).hasSize(1);
            assertThat(((Number)listed.getFirst().get("Results")).intValue()).isEqualTo(1);
            assertThat(sql.queryForList("EXEC Person.GetUsers 100,0,NULL,N'list-upgrade',N'User',NULL,NULL")).isEmpty();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            flyway.validate();
        } finally {
            admin.execute("ALTER DATABASE ["+name+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
            admin.execute("DROP DATABASE ["+name+"]");
        }
    }

}
