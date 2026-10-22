package com.chellavignesh.authserver.database;

import com.chellavignesh.authserver.adminportal.application.*;
import com.chellavignesh.authserver.adminportal.application.dto.*;
import com.chellavignesh.authserver.adminportal.application.entity.*;
import com.chellavignesh.authserver.adminportal.forgotusername.ForgotUsernameSetting;
import com.chellavignesh.authserver.adminportal.resource.ResourceRepository;
import com.chellavignesh.authserver.adminportal.resource.dto.ResourceLibraryDto;
import com.chellavignesh.authserver.adminportal.resource.dto.HttpMethodEnum;
import com.chellavignesh.authserver.adminportal.user.UserRepository;
import com.chellavignesh.authserver.adminportal.user.dto.CreateUserDto;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.chellavignesh.authserver.adminportal.util.SecurityUtil;
import com.chellavignesh.authserver.authcode.entity.AuthCode;
import com.chellavignesh.authserver.enums.EnumRepository;
import com.chellavignesh.authserver.enums.entity.*;
import com.chellavignesh.authserver.pkce.entity.Pkce;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Creates and drops only a uniquely named database; never migrates a supplied application database. */
@EnabledIfEnvironmentVariable(named = "AUTH_DB_IT_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseContractTest {
    private final String database = "AuthserverContract_" + UUID.randomUUID().toString().replace("-", "");
    private SQLServerDataSource admin;
    private JdbcTemplate jdbc;
    private NamedParameterJdbcTemplate named;
    private ApplicationRepository applications;
    private UserRepository users;
    private final UUID source = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeAll
    void installFreshDatabase() {
        admin = source(System.getenv("AUTH_DB_IT_URL"));
        new JdbcTemplate(admin).execute("CREATE DATABASE [" + database + "]");
        SQLServerDataSource db = source(System.getenv("AUTH_DB_IT_URL") + ";databaseName=" + database);
        Flyway flyway = Flyway.configure().dataSource(db).locations("classpath:db/migration").cleanDisabled(true).load();
        assertEquals(13, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted, "Second migration must leave the schema unchanged");
        jdbc = new JdbcTemplate(db);
        named = new NamedParameterJdbcTemplate(db);
        new EnumRepository(named).loadEnumValues();
        applications = new ApplicationRepository(named, new SecurityUtil());
        ReflectionTestUtils.setField(applications, "serverBasePath", "http://localhost:8080/services");
        users = new UserRepository(named, new SecurityUtil());
        for (var status : com.chellavignesh.authserver.adminportal.credential.CredentialStatus.values()) {
            assertNotNull(status.getValue(), "Every credential enum must be loaded");
        }
    }

    private SQLServerDataSource source(String url) {
        SQLServerDataSource ds = new SQLServerDataSource();
        ds.setURL(url);
        ds.setUser(System.getenv().getOrDefault("AUTH_DB_IT_USER", "sa"));
        ds.setPassword(Objects.requireNonNull(System.getenv("AUTH_DB_IT_PASSWORD"), "AUTH_DB_IT_PASSWORD"));
        return ds;
    }

    @AfterAll
    void removeOnlyOwnedDatabase() {
        if (admin != null) {
            JdbcTemplate control = new JdbcTemplate(admin);
            control.execute("ALTER DATABASE [" + database + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
            control.execute("DROP DATABASE [" + database + "]");
        }
    }

    private Application application() throws Exception {
        CreateApplicationDto app = new CreateApplicationDto();
        app.setName("contract-" + UUID.randomUUID());
        app.setDescription("SQL Server contract test");
        app.setType(ApplicationTypeEnum.WEB);
        app.setAuthMethod(AuthFlowEnum.PKCE);
        app.setUsernameType(UsernameTypeEnum.USERNAME);
        app.setAllowForgotUsername(false);
        UpdateTokenSettingsDto settings = new UpdateTokenSettingsDto();
        settings.setAuthCodeTimeToLive(120);
        settings.setAccessTokenTimeToLive(600);
        settings.setRefreshTokenTimeToLive(3600);
        settings.setDeviceCodeTimeToLive(60);
        settings.setMaxRequestTransitTime(20);
        settings.setReuseRefreshTokens(false);
        return applications.create(1, app, settings, new ForgotUsernameSetting(List.of()), AccessTokenFormatEnum.REFERENCE);
    }

    @Test
    void repositoryApplicationSettingsRedirectsAndResourcesRoundTrip() throws Exception {
        Application app = application();
        assertTrue(applications.exists(1, app.getRowGuid()));
        assertTrue(applications.existsByName(app.getName(), 1));
        assertEquals(app.getId(), applications.getByClientId(app.getClientId()).orElseThrow().getId());
        assertNotNull(applications.getSettingsByApplicationId(app.getId()).getJWSAlgorithm());
        var detail = applications.getApplicationDetailById(app.getId()).orElseThrow();
        assertEquals(120, detail.getAuthCodeTimeToLive());
        assertEquals(20, detail.getMaxRequestTransitTime());
        new RedirectUriRepository(named, new SecurityUtil()).createRedirectUris(1,app.getId(),List.of("http://localhost/callback"));
        PostLogoutRedirectUriRepository logout = new PostLogoutRedirectUriRepository(named,new SecurityUtil());
        logout.createPostLogoutRedirectUris(1,app.getId(),List.of("http://localhost/logout"));
        logout.updatePostLogoutRedirectUri(1,app.getId(),"http://localhost/bye");
        assertEquals(Set.of("http://localhost/callback"),applications.getApplicationRedirectUris(app.getId()));
        assertEquals(Set.of("http://localhost/bye"),applications.getApplicationLogoutRedirectUris(app.getId()));
        ResourceLibraryDto resource = new ResourceLibraryDto();
        resource.setName("Resource"); resource.setDescription("Contract"); resource.setUri("https://example.test/"+UUID.randomUUID());
        resource.setAllowedMethod("GET"); resource.setUrn("urn:contract");
        var library = new ResourceRepository(named).create(resource);
        var assigned = applications.assignResource(1,app.getId(),library.getId());
        assertEquals(library.getId(),assigned.getResourceLibraryId());
        assertEquals(library.getId(),applications.getAllAssignedResourcesByClientId(app.getClientId()).getFirst().getResourceLibraryId());
        assertFalse(applications.inactivateApplication(1,app.getId(),false) && applications.getById(app.getId()).orElseThrow().getActive());
    }

    @Test
    void usersAreBrandScopedAndBinaryPasswordsMapWithoutTruncation() throws Exception {
        CreateUserDto dto = new CreateUserDto();
        dto.setOrgId(1); dto.setFirstName("Contract"); dto.setLastName("User"); dto.setUsername("same-name");
        dto.setEmail("contract@example.test"); dto.setPhoneNumber("+19999999999"); dto.setSyncFlag(true);
        dto.setMetaData(Map.of("contract", "value"));
        String passwordHex="00AABBCCDDEEFF010203040506070809";
        var user = users.create(dto,passwordHex,1,1,source,12);
        assertEquals(passwordHex,users.getUserAuthDetailsByUsernameAndExternalSourceCode("same-name","idp").orElseThrow().getPassword());
        assertEquals(passwordHex,users.getCredentialsByUsernameAndBranding("same-name","idp").orElseThrow().getPassword());
        assertEquals("value",users.getMetadata(user.getRowGuid()).getFirst().value());
        UserDetails details=users.getByGuidAndBranding(user.getRowGuid(),"idp").orElseThrow();
        assertEquals("same-name",details.username()); assertNotNull(details.profileOrgId()); assertNotNull(details.groupId());
        assertTrue(users.getByGuidAndBranding(user.getRowGuid(),"other").isEmpty());
        UUID other=UUID.randomUUID();
        jdbc.update("INSERT dbo.ExternalSource(SourceId,SourceCode,ExternalTypeId) VALUES(?,?,1)",other.toString(),"other");
        var second=users.create(dto,"FFEEDDCCBBAA",1,0,other,12);
        assertEquals(second.getRowGuid(),users.getUserByUsernameAndBranding("same-name","other").orElseThrow().getRowGuid());
        assertThrows(DataAccessException.class,()->users.getUserAuthDetailsByUserName("same-name"));
        users.updateAccessFailedCountWithExternalSourceCode("same-name","idp",0,2);
        var locked=users.updateAccessFailedCountWithExternalSourceCode("same-name","idp",0,2).orElseThrow();
        assertTrue(locked.getCredentialLocked());
        assertFalse(users.getUserAuthDetailsByUsernameAndExternalSourceCode("same-name","other").orElseThrow().getCredentialLocked());
        assertEquals(user.getId(),users.getProfile(user.getRowGuid()).orElseThrow().getId());
    }

    @Test
    void oauthCodesPkceExpiryConsumptionAndTokenRotationAreAtomic() throws Exception {
        Application app=application();
        int session=jdbc.queryForObject("EXEC Token.CreateAuthSession ?,?,?,?,?,?,?",Integer.class,
                app.getId(),"contract-subject","openid",AuthFlowEnum.PKCE.getValue(),new byte[]{1},app.getClientId(),"idp");
        String sessionId=jdbc.queryForObject("SELECT CONVERT(VARCHAR(36),SessionId) FROM Token.AuthSession WHERE AuthSessionId=?",String.class,session);
        String code="code-"+UUID.randomUUID();
        int codeId=jdbc.queryForObject("EXEC Token.CreateAuthCode ?,?,?",Integer.class,app.getId(),sessionId,code);
        int pkce=jdbc.queryForObject("EXEC Token.CreatePkce ?,?,?,?,?",Integer.class,app.getId(),sessionId,"challenge","S256","http://localhost/callback");
        AuthCode mapped=named.query("EXEC Token.GetAuthCodeById :id",Map.of("id",codeId),(rs,n)->AuthCode.fromResult(rs)).getFirst();
        assertNull(mapped.getConsumedOn());
        Pkce mappedPkce=named.query("EXEC Token.GetPkceById :id",Map.of("id",pkce),(rs,n)->Pkce.fromResult(rs)).getFirst();
        assertNotNull(mappedPkce.getExpiration()); assertTrue(mappedPkce.getExpiration().after(mappedPkce.getCreatedOn()));
        Timestamp issued=Timestamp.from(Instant.now().minusSeconds(5));
        jdbc.update("UPDATE Token.AuthSession SET Pin=? WHERE SessionId=?","123456,"+java.time.LocalDateTime.ofInstant(issued.toInstant(),java.time.ZoneOffset.UTC),sessionId);
        var expiry=new MfaExpiryPinTimeRepository(named).getMfaExpiryPinTime(300,UUID.fromString(sessionId));
        assertTrue(expiry.getTimeToExpireMS()>290_000 && expiry.getTimeToExpireMS()<=300_000);
        int codeToken = jdbc.queryForObject("EXEC Token.CreateTokenWithHash ?,?,?,?,?,?,?,?,?", Integer.class,
                TokenTypeEnum.CODE.getValue(),app.getId(),"contract-subject",sessionId,true,code,
                new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.VARBINARY,null),600,
                java.security.MessageDigest.getInstance("SHA-256").digest(code.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        Callable<Boolean> consume=()->{start.await();try {jdbc.update("EXEC Token.SetAuthCodeConsumedOn ?",code);return true;}catch(DataAccessException e){return false;}};
        try {
            Future<Boolean> one=executor.submit(consume); Future<Boolean> two=executor.submit(consume); start.countDown();
            assertNotEquals(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS),"Exactly one concurrent exchange succeeds");
        } finally {executor.shutdownNow();}
        assertNotNull(named.query("EXEC Token.GetAuthCodeById :id",Map.of("id",codeId),(rs,n)->AuthCode.fromResult(rs)).getFirst().getConsumedOn());
        assertTrue(jdbc.queryForList("EXEC Token.GetSessionIdByAuthCode ?",code).isEmpty());
        assertNotNull(jdbc.queryForObject("SELECT ConsumedOn FROM Token.Token WHERE TokenId=?",Timestamp.class,codeToken));
        assertNotNull(jdbc.queryForObject("SELECT ConsumedOn FROM Token.Pkce WHERE PkceId=?",Timestamp.class,pkce));
        int token=jdbc.queryForObject("EXEC Token.CreateTokenWithHash ?,?,?,?,?,?,?,?,?",Integer.class,TokenTypeEnum.REFRESH_TOKEN.getValue(),app.getId(),"contract-subject",sessionId,true,"refresh",new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.VARBINARY,null),600,new byte[32]);
        assertEquals(1,jdbc.queryForList("EXEC Token.GetAllActiveTokensBySessionId ?",sessionId).size());
        jdbc.update("EXEC Token.ConsumeToken ?",token);
        assertThrows(DataAccessException.class,()->jdbc.update("EXEC Token.ConsumeToken ?",token));
        assertTrue(jdbc.queryForList("EXEC Token.GetAllActiveTokensBySessionId ?",sessionId).isEmpty());
    }
    @Test
    void springAuthorizationStatePreservesTokenTypesAndMetadata() {
        var registered = org.springframework.security.oauth2.server.authorization.client.RegisteredClient.withId("sql-contract")
                .clientId("sql-contract")
                .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.NONE)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://example.test/callback").scope("openid").build();
        var clients = new org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository(registered);
        var service = new org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService(jdbc, clients);
        var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var code = new org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode("sql-code", now, now.plusSeconds(120));
        var access = new org.springframework.security.oauth2.core.OAuth2AccessToken(
                org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER,"sql-access",now,now.plusSeconds(600),Set.of("openid"));
        var authorization = org.springframework.security.oauth2.server.authorization.OAuth2Authorization.withRegisteredClient(registered)
                .id("sql-state").principalName("sql-user")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid")).attribute("contract", "value")
                .token(code).accessToken(access)
                .refreshToken(new org.springframework.security.oauth2.core.OAuth2RefreshToken("sql-refresh",now,now.plusSeconds(3600)))
                .build();
        service.save(authorization);
        var loaded = service.findByToken("sql-access",org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN);
        assertNotNull(loaded);
        assertEquals("value",loaded.getAttribute("contract"));
        assertEquals("sql-user",loaded.getPrincipalName());
        assertEquals(Set.of("openid"),loaded.getAuthorizedScopes());
        assertEquals("sql-refresh",loaded.getRefreshToken().getToken().getTokenValue());
        assertEquals(now,loaded.getAccessToken().getToken().getIssuedAt());
    }

    @Test
    void signatureLookupCanSeeNewTokenWithoutRoundingIssuanceIntoTheFuture() throws Exception {
        Application app=application();
        int session=jdbc.queryForObject("EXEC Token.CreateAuthSession ?,?,?,?,?,?,?",Integer.class,
                app.getId(),"precision-subject","openid",AuthFlowEnum.PKCE.getValue(),new byte[]{1},app.getClientId(),"idp");
        String sessionId=jdbc.queryForObject("SELECT CONVERT(VARCHAR(36),SessionId) FROM Token.AuthSession WHERE AuthSessionId=?",String.class,session);
        var issued=java.time.LocalDateTime.of(2026,1,1,12,0,0,900_000_000);
        long token=jdbc.queryForObject("EXEC Token.CreateTokenWithHash ?,?,?,?,?,?,?,?,?,?,?,?",Long.class,
                TokenTypeEnum.ACCESS_TOKEN.getValue(),app.getId(),"precision-subject",sessionId,true,"precision-token-"+UUID.randomUUID(),
                new byte[32],600,new byte[32],issued,issued.plusMinutes(10),"openid");
        var visible=jdbc.queryForList("EXEC Token.GetTokensByClientIdAndRequestDateTime ?,?",app.getClientId(),issued.plusNanos(1_000_000));
        assertTrue(visible.stream().anyMatch(row->((Number)row.get("TokenId")).longValue()==token),
                "A token issued at .900 seconds must be visible at .901, without waiting for the next whole second");
    }

}
