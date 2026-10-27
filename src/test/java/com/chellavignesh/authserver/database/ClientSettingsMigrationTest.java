package com.chellavignesh.authserver.database;

import com.chellavignesh.authserver.adminportal.application.ApplicationRepository;
import com.chellavignesh.authserver.adminportal.application.dto.CreateApplicationDto;
import com.chellavignesh.authserver.adminportal.application.dto.UpdateTokenSettingsDto;
import com.chellavignesh.authserver.adminportal.forgotusername.ForgotUsernameSetting;
import com.chellavignesh.authserver.adminportal.util.SecurityUtil;
import com.chellavignesh.authserver.enums.EnumRepository;
import com.chellavignesh.authserver.enums.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="AUTH_DB_IT_URL",matches=".+")
class ClientSettingsMigrationTest {
    SQLServerDataSource source(String url) {
        var ds=new SQLServerDataSource();ds.setURL(url);ds.setUser(System.getenv("AUTH_DB_IT_USER"));ds.setPassword(System.getenv("AUTH_DB_IT_PASSWORD"));return ds;
    }
    byte[] snapshot(JdbcTemplate sql)throws Exception {
        var rows=new TreeMap<String,Object>();
        for(String table:List.of("Client.Application","Client.TokenSetting","Client.ForgetUserNameSetting"))rows.put(table,sql.queryForList("SELECT * FROM "+table));
        return new ObjectMapper().findAndRegisterModules().writeValueAsBytes(rows);
    }
    @Test
    void populatedFifteenUpgradePreservesBothResultSetsAndPersistedTrueFalseReuse()throws Exception {
        String url=System.getenv("AUTH_DB_IT_URL");assertTrue(url.matches("jdbc:sqlserver://(?:127\\.0\\.0\\.1|localhost):15433;encrypt=true;trustServerCertificate=true"));
        String database="AuthserverClientUpgrade_"+UUID.randomUUID().toString().replace("-","");
        var admin=new JdbcTemplate(source(url));admin.execute("CREATE DATABASE ["+database+"]");
        try {
            var ds=source(url+";databaseName="+database);var sql=new JdbcTemplate(ds);
            assertEquals(15,Flyway.configure().dataSource(ds).target("15").load().migrate().migrationsExecuted);
            var named=new NamedParameterJdbcTemplate(ds);new EnumRepository(named).loadEnumValues();
            var applications=new ApplicationRepository(named,new SecurityUtil());ReflectionTestUtils.setField(applications,"serverBasePath","http://localhost:8080/services");
            var ids=new ArrayList<Integer>();
            for(boolean reuse:List.of(false,true)) {
                var app=new CreateApplicationDto();app.setName("Preserved "+reuse);app.setDescription("Owned migration fixture");app.setType(ApplicationTypeEnum.WEB);app.setAuthMethod(AuthFlowEnum.PKCE);app.setUsernameType(UsernameTypeEnum.USERNAME);app.setAllowForgotUsername(false);
                var settings=new UpdateTokenSettingsDto();settings.setAuthCodeTimeToLive(120);settings.setAccessTokenTimeToLive(600);settings.setRefreshTokenTimeToLive(3600);settings.setDeviceCodeTimeToLive(300);settings.setMaxRequestTransitTime(30);settings.setReuseRefreshTokens(reuse);
                int id=applications.create(1,app,settings,new ForgotUsernameSetting(List.of()),AccessTokenFormatEnum.REFERENCE).getId();ids.add(id);
                sql.update("INSERT Client.ForgetUserNameSetting(OrganizationId,ApplicationId,ParamPriority,ParamJson) VALUES(1,?,0,'{\"lookupCriteria\":[\"email\"]}')",id);
            }
            byte[] before=snapshot(sql);var flyway=Flyway.configure().dataSource(ds).target("16").load();assertEquals(1,flyway.migrate().migrationsExecuted);
            assertTrue(Arrays.equals(before,snapshot(sql)),"V16 must preserve existing client settings and username rows");
            for(int index=0;index<ids.size();index++) {
                var detail=applications.getApplicationDetailById(ids.get(index)).orElseThrow();assertEquals(index==1,detail.getReuseRefreshTokens());assertEquals(120,detail.getAuthCodeTimeToLive());assertEquals(1,detail.getUsernameLookupCriteria().size());
            }
            assertEquals(0,flyway.migrate().migrationsExecuted);flyway.validate();assertTrue(Arrays.equals(before,snapshot(sql)));
        } finally {admin.execute("ALTER DATABASE ["+database+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");admin.execute("DROP DATABASE ["+database+"]");}
    }
}
