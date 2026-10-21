package com.chellavignesh.authserver.security;

import com.chellavignesh.authserver.adminportal.application.*;
import com.chellavignesh.authserver.adminportal.application.dto.*;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.forgotusername.ForgotUsernameSetting;
import com.chellavignesh.authserver.adminportal.credential.CredentialService;
import com.chellavignesh.authserver.adminportal.credential.CredentialStatus;
import com.chellavignesh.authserver.adminportal.credential.dto.CreateCredentialRequestDto;
import com.chellavignesh.authserver.adminportal.resource.ResourceRepository;
import com.chellavignesh.authserver.adminportal.resource.dto.ResourceLibraryDto;
import com.chellavignesh.authserver.config.PreAuthGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import com.chellavignesh.authserver.adminportal.organization.*;
import com.chellavignesh.authserver.adminportal.organization.dto.CreateOrganizationDto;
import com.chellavignesh.authserver.adminportal.organization.entity.Organization;
import com.chellavignesh.authserver.adminportal.user.*;
import com.chellavignesh.authserver.adminportal.user.dto.CreateUserDto;
import com.chellavignesh.authserver.adminportal.user.entity.User;
import com.chellavignesh.authserver.enums.entity.*;
import com.chellavignesh.authserver.session.fingerprint.ClientFingerprint;
import com.fasterxml.jackson.databind.*;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** All fixtures live in a new UUID database. No shared records, processes or keys are changed. */
@EnabledIfEnvironmentVariable(named="AUTH_APP_IT", matches="true")
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "app.local.bootstrap.enabled=false", "toggles.signature.required=true",
        "logging.level.root=WARN", "logback.access.enabled=false"})
class ManagementPolicyHttpIntegrationTest {
    static final String database="AuthserverPolicy_"+UUID.randomUUID().toString().replace("-","");
    static final String namespace="authserver:policy:"+UUID.randomUUID()+":";
    static JdbcTemplate admin;
    static boolean owned;
    @DynamicPropertySource static void isolate(DynamicPropertyRegistry registry) {
        String url=System.getenv("AUTH_DB_IT_URL");
        if (url==null || !url.matches("jdbc:sqlserver://(?:127\\.0\\.0\\.1|localhost):15433;encrypt=true;trustServerCertificate=true"))
            throw new IllegalStateException("Policy tests require the approved loopback SQL Server");
        SQLServerDataSource ds=new SQLServerDataSource(); ds.setURL(url);
        ds.setUser(System.getenv("AUTH_DB_IT_USER")); ds.setPassword(System.getenv("AUTH_DB_IT_PASSWORD"));
        admin=new JdbcTemplate(ds); admin.execute("CREATE DATABASE ["+database+"]"); owned=true;
        Runtime.getRuntime().addShutdownHook(new Thread(ManagementPolicyHttpIntegrationTest::dropOwnedDatabase));
        registry.add("spring.datasource.url",()->url+";databaseName="+database);
        registry.add("spring.session.redis.namespace",()->namespace+"session");
        registry.add("spring.cache.redis.key-prefix",()->namespace+"cache:");
    }
    static synchronized void dropOwnedDatabase() {
        if (owned) {
            admin.execute("ALTER DATABASE ["+database+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
            admin.execute("DROP DATABASE ["+database+"]"); owned=false;
        }
    }
    @Autowired JdbcTemplate sql;
    @Autowired OrganizationService organizations;
    @Autowired ApplicationRepository applications;
    @Autowired UserRepository users;
    @Autowired RedirectUriRepository redirects;
    @Autowired ObjectMapper mapper;
    @Autowired CredentialService credentials;
    @Autowired ResourceRepository resources;
    @Autowired RedisIndexedSessionRepository sessions;
    @Autowired @Qualifier("redisTemplate") RedisTemplate<String,Object> sessionRedis;
    @Autowired @Qualifier("cacheRedisTemplate") RedisTemplate<String,Object> cacheRedis;
    @LocalServerPort int port;
    Organization orgA,orgB;
    User adminA, developer, viewer, account, localUser, foreignUser, platform;
    Application app;
    Fixture adminToken,developerToken,viewerToken,accountToken,platformToken;
    int adminGroup;
    final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    record Fixture(String value,byte[] key) {}
    @BeforeAll void fixtures() throws Exception {
        orgA=organization("Policy A"); orgB=organization("Policy B");
        adminGroup=group(orgA,"Eligible administrator", List.of("idp-admin-user:read","idp-admin-user:create","idp-admin-user:update","idp-admin-user:delete", "idp-admin-admin:read","idp-admin-admin:create","idp-admin-admin:update","idp-admin-admin:delete", "idp-admin-org:read","idp-admin-org:update"));
        int devGroup=group(orgA,"Developer",List.of("idp-admin-app:read"));
        int viewerGroup=group(orgA,"Viewer",List.of("idp-admin-org:read"));
        adminA=user(orgA,adminGroup,"admin-a"); developer=user(orgA,devGroup,"developer-a"); viewer=user(orgA,viewerGroup,"viewer-a");
        account=user(orgA,0,"account-a");localUser=user(orgA,0,"local-a");foreignUser=user(orgB,0,"foreign-b");
        platform=user(organizations.getById(1).orElseThrow(),1,"platform");
        app=application(orgA);
        redirects.createRedirectUris(orgA.getId(),app.getId(),List.of("http://localhost/callback"));
        adminToken=token(adminA);developerToken=token(developer);viewerToken=token(viewer);accountToken=token(account);platformToken=token(platform);
    }
    @AfterAll void cleanup() {
        for (RedisTemplate<String,Object> redis:List.of(sessionRedis,cacheRedis)) {
            // The literal UUID namespace owns every key selected here; never flush a Redis DB.
            Set<String> keys=redis.keys(namespace+"*");
            if(keys!=null && !keys.isEmpty()) redis.delete(keys);
        }
        dropOwnedDatabase();
    }
    Organization organization(String name) throws Exception {var dto=new CreateOrganizationDto();dto.setName(name);dto.setDescription("Disposable policy fixture");return organizations.create(dto);}
    int group(Organization org,String name,List<String> permissions) {
        int id=sql.queryForObject("INSERT Partner.OrganizationGroup(OrganizationId,GroupName) OUTPUT INSERTED.OrganizationGroupId VALUES(?,?)",Integer.class,org.getId(),name);
        for (String key:permissions) sql.update("INSERT Partner.OrganizationGroupPermission(OrganizationGroupId,OrganizationId,PermissionName,PermissionKey) VALUES(?,?,?,?)",id,org.getId(),key,key);
        return id;
    }
    User user(Organization org,int group,String name) throws Exception {
        var dto=new CreateUserDto(); dto.setOrgId(org.getId());dto.setFirstName("Policy");dto.setLastName(name);
        dto.setUsername(name);dto.setEmail(name+"@example.test");dto.setPhoneNumber("+19999999999");dto.setSyncFlag(false);
        dto.setMetaData(Map.of("marker",name));
        return users.create(dto,"0011223344556677",1,group,UUID.fromString("11111111-1111-1111-1111-111111111111"),12);
    }
    Application application(Organization org) throws Exception {
        var dto=new CreateApplicationDto();dto.setName("Policy app");dto.setDescription("Fixture");dto.setType(ApplicationTypeEnum.WEB);dto.setAuthMethod(AuthFlowEnum.PKCE);dto.setUsernameType(UsernameTypeEnum.USERNAME);dto.setAllowForgotUsername(false);
        var settings=new UpdateTokenSettingsDto();settings.setAuthCodeTimeToLive(120);settings.setAccessTokenTimeToLive(600);settings.setRefreshTokenTimeToLive(3600);settings.setDeviceCodeTimeToLive(120);settings.setMaxRequestTransitTime(30);settings.setReuseRefreshTokens(false);
        return applications.create(org.getId(),dto,settings,new ForgotUsernameSetting(List.of()),AccessTokenFormatEnum.REFERENCE);
    }
    Fixture token(User user) throws Exception {
        String username=users.getByGuid(user.getRowGuid()).orElseThrow().username();
        return token(app,username,"openid metadata idp-admin");
    }
    Fixture token(Application client,String subject,String scope) throws Exception {
        byte[] fingerprint=new ClientFingerprint(ZoneOffset.UTC,"en-US","PolicyIntegration/1.0",null).getBytes();
        int session=sql.queryForObject("EXEC Token.CreateAuthSession ?,?,?,?,?,?,?",Integer.class,client.getId(),subject,scope,client.getAuthFlow().getValue(),fingerprint,client.getClientId(),"idp");
        String sessionId=sql.queryForObject("SELECT CONVERT(VARCHAR(36),SessionId) FROM Token.AuthSession WHERE AuthSessionId=?",String.class,session);
        String value="policy-"+UUID.randomUUID();byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);
        sql.queryForObject("EXEC Token.CreateTokenWithHash ?,?,?,?,?,?,?,?,?",Integer.class,TokenTypeEnum.ACCESS_TOKEN.getValue(),client.getId(),subject,sessionId,true,value,key,600,MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        return new Fixture(value,key);
    }
    HttpResponse<String> call(Fixture token,String method,String path,String body) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/services"+path)).timeout(Duration.ofSeconds(20))
                .header("User-Agent","PolicyIntegration/1.0").header("Accept-Language","en-US")
                .header("x-request-datetime",OffsetDateTime.now(ZoneOffset.UTC).toString());
        if(token!=null) {var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(token.key(),"HmacSHA256"));b.header("Authorization","Bearer "+token.value()).header("x-signature",Base64.getEncoder().encodeToString(mac.doFinal((body==null?"":body).getBytes(StandardCharsets.UTF_8))));}
        if(body!=null)b.header("Content-Type","application/json");
        return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String path(User user) {return "/api/v1/users/"+user.getRowGuid();}
    @Test void tenantReadWithoutFilterIsScoped() throws Exception {
        var response=call(adminToken,"GET","/api/v1/users",null);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body=mapper.readTree(response.body());
        assertThat(body.path("results").asInt()).isEqualTo(sql.queryForObject("SELECT COUNT(*) FROM Person.ProfileOrganization WHERE OrganizationId=?",Integer.class,orgA.getId()));
        assertThat(response.body()).doesNotContain("foreign-b",foreignUser.getRowGuid().toString(),"platform@example.test");
    }
    @Test void forgedOrganizationFilterIsDenied() throws Exception {
        var response=call(adminToken,"GET","/api/v1/users?organizationId="+orgB.getRowGuid(),null);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).doesNotContain("foreign-b");
    }
    @Test void foreignUserMetadataIsDenied() throws Exception {
        for(String suffix:List.of("","/metadata")) {var response=call(adminToken,"GET",path(foreignUser)+suffix,null);assertThat(response.statusCode()).isEqualTo(403);assertThat(response.body()).doesNotContain("foreign-b");}
    }
    @Test void orgAdminCanManageEligibleLocalUser() throws Exception {
        var response=call(adminToken,"PUT",path(localUser)+"/metadata","{\"metaData\":{\"policy\":\"allowed\"}}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(users.getMetadata(localUser.getRowGuid())).anyMatch(item->item.key().equals("policy")&&item.value().equals("allowed"));
    }
    @Test void developerCannotManageUsers() throws Exception {
        for(Fixture actor:List.of(developerToken,viewerToken,accountToken)) {
            assertThat(call(actor,"GET","/api/v1/users",null).statusCode()).isEqualTo(403);
            assertThat(call(actor,"PUT",path(foreignUser)+"/metadata","{\"metaData\":{\"marker\":\"denied\"}}").statusCode()).isEqualTo(403);
        }
        assertThat(users.getMetadata(foreignUser.getRowGuid())).anyMatch(item->item.key().equals("marker")&&item.value().equals("foreign-b"));
    }
    @Test void permissionRemovalAffectsNextRequest() throws Exception {
        int group=group(orgA,"Revocable",List.of("idp-admin-user:update"));User actor=user(orgA,group,"revocable");Fixture bearer=token(actor);
        assertThat(call(bearer,"PUT",path(localUser)+"/metadata","{\"metaData\":{\"revocation\":\"before\"}}").statusCode()).isEqualTo(200);
        sql.update("UPDATE Partner.OrganizationGroupPermission SET Status=0 WHERE OrganizationGroupId=?",group);
        assertThat(call(bearer,"PUT",path(localUser)+"/metadata","{\"metaData\":{\"revocation\":\"after\"}}").statusCode()).isEqualTo(403);
        assertThat(users.getMetadata(localUser.getRowGuid())).anyMatch(item->item.key().equals("revocation")&&item.value().equals("before"));
    }
    @Test void deactivatedActorOrganizationCannotAdminister() throws Exception {
        Organization org=organization("Revoked tenant");int group=group(org,"Admin",List.of("idp-admin-user:read"));User actor=user(org,group,"revoked");Fixture bearer=token(actor);
        sql.update("UPDATE Partner.Organization SET Status=0 WHERE OrganizationId=?",org.getId());
        assertThat(call(bearer,"GET","/api/v1/users",null).statusCode()).isIn(401,403);
    }
    @Test void cookieOnlyManagementRequestIsUnauthorized() throws Exception {
        for(boolean pending:List.of(false,true)) {
            var session=sessions.createSession();
            var auth=UsernamePasswordAuthenticationToken.authenticated("fixture",null,pending?List.of(new PreAuthGrantedAuthority()):List.of());
            session.setAttribute("SPRING_SECURITY_CONTEXT",new SecurityContextImpl(auth));sessions.save(session);
            try {
                String cookie="SESSION="+Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8));
                for(String path:List.of("/api/v1/users","/api/v2/organizations/"+orgA.getRowGuid()+"/applications")) {
                    var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/services"+path)).header("Cookie",cookie)
                            .header("x-request-datetime",OffsetDateTime.now(ZoneOffset.UTC).toString()).GET().build();
                    assertThat(http.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
                }
            } finally {sessions.deleteById(session.getId());}
        }
    }
    @Test void hostedLoginStillRejectsBadCsrf() throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/services/login"))
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=fixture&password=invalid&_csrf=forged")).build();
        assertThat(http.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }
    @Test void platformCanSelectTenantWithoutChangingIdentity() throws Exception {
        var response=call(platformToken,"GET","/api/v1/users?organizationId="+orgB.getRowGuid(),null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).path("results").asInt()).isEqualTo(1);
        assertThat(response.body()).contains("foreign-b").doesNotContain("local-a");
    }
    @Test void ordinaryReadPermissionCannotRevealAdministratorRowsOrTotals() throws Exception {
        User reader=user(orgA,group(orgA,"User reader",List.of("idp-admin-user:read")),"reader");
        var response=call(token(reader),"GET","/api/v1/users",null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).path("results").asInt()).isEqualTo(2);
        assertThat(response.body()).doesNotContain("admin-a","developer-a","viewer-a","reader@example.test","foreign-b");
    }
    @Test void machineCannotTargetForeignOrg() throws Exception {
        Organization org=orgA;
        var dto=new CreateApplicationDto();dto.setName("Policy machine");dto.setDescription("Fixture");dto.setType(ApplicationTypeEnum.SERVER);dto.setAuthMethod(AuthFlowEnum.CLIENT_SECRET_JWT);dto.setUsernameType(UsernameTypeEnum.USERNAME);dto.setAllowForgotUsername(false);
        var settings=new UpdateTokenSettingsDto();settings.setAuthCodeTimeToLive(120);settings.setAccessTokenTimeToLive(600);settings.setRefreshTokenTimeToLive(3600);settings.setDeviceCodeTimeToLive(120);settings.setMaxRequestTransitTime(30);settings.setReuseRefreshTokens(false);
        Application machine=applications.create(org.getId(),dto,settings,new ForgotUsernameSetting(List.of()),AccessTokenFormatEnum.REFERENCE);
        var secret=new CreateCredentialRequestDto();secret.setOrgId(org.getId());secret.setAppId(machine.getId());secret.setName("Policy machine credential");secret.setDescription("Fixture");secret.setAlgorithmEnum(AlgorithmEnum.HS256);secret.setAuthFlow(AuthFlowEnum.CLIENT_SECRET_JWT);secret.setCredentialStatus(CredentialStatus.Active);secret.setExpireOn(Date.from(Instant.now().plusSeconds(86400)));
        credentials.createSharedSecretCredential(secret);
        for(String method:List.of("GET","PUT")) {
            var resource=new ResourceLibraryDto();resource.setName("Policy user "+method);resource.setDescription("Fixture");resource.setUri("http://user");resource.setAllowedMethod(method);resource.setUrn("urn:policy:"+method);
            var created=resources.create(resource);applications.assignResource(org.getId(),machine.getId(),created.getId());
        }
        Fixture bearer=token(machine,machine.getClientId(),"confidential-client");
        assertThat(call(bearer,"GET",path(localUser),null).statusCode()).isEqualTo(200);
        assertThat(call(bearer,"GET",path(foreignUser)+"/metadata",null).statusCode()).isEqualTo(403);
        assertThat(call(bearer,"PUT",path(foreignUser)+"/metadata","{\"metaData\":{\"marker\":\"denied\"}}").statusCode()).isEqualTo(403);
        assertThat(call(bearer,"PUT",path(localUser)+"/metadata","{\"metaData\":{\"machine\":\"allowed\"}}").statusCode()).isEqualTo(200);
        assertThat(users.getMetadata(localUser.getRowGuid())).anyMatch(item->item.key().equals("machine")&&item.value().equals("allowed"));
        assertThat(users.getMetadata(foreignUser.getRowGuid())).anyMatch(item->item.key().equals("marker")&&item.value().equals("foreign-b"));
    }
    @Test void profileBodyCannotReactivateForeignMemberWithLocalOrganization() throws Exception {
        String schema="urn:ascensus:529:1.0:accountsync";
        var payload=Map.of("branding","idp","intent","conversion","timestamp",Instant.now().toString(),"orgId",orgA.getRowGuid(),
                "credential",Map.of("loginId",UUID.randomUUID(),"username","foreign-b","passwordHash","001122","cIndex","0"),
                "profile",Map.of("memberId",foreignUser.getRowGuid(),"firstName","Policy","lastName","foreign-b","email","foreign-b@example.test","phoneNumber","+19999999999","idpSyncDttm",Instant.now().toString()),
                "metadata",Map.of("idp",List.of(),"unite",List.of()),"syncFlag",false);
        sql.update("UPDATE Person.Profile SET Status=0 WHERE RowGuid=?",foreignUser.getRowGuid().toString());
        try {
            int response=call(adminToken,"POST","/api/v1/users/profile",mapper.writeValueAsString(Map.of("schema",schema,schema,payload))).statusCode();
            assertThat(response).isEqualTo(403);
            assertThat(sql.queryForObject("SELECT Status FROM Person.Profile WHERE RowGuid=?",Integer.class,foreignUser.getRowGuid().toString())).isZero();
        } finally { sql.update("UPDATE Person.Profile SET Status=1 WHERE RowGuid=?",foreignUser.getRowGuid().toString()); }
    }
    @Test void platformCanReadAndUpdateRevokedOrganization() throws Exception {
        Organization org=organization("Platform status fixture");
        String path="/api/v1/organizations/"+org.getRowGuid();
        assertThat(call(platformToken,"PUT",path,"{\"name\":\"Platform status fixture\",\"description\":\"Fixture\",\"status\":\"Revoked\"}").statusCode()).isEqualTo(200);
        assertThat(sql.queryForObject("SELECT Status FROM Partner.Organization WHERE OrganizationId=?",Integer.class,org.getId())).isZero();
        assertThat(call(platformToken,"GET",path,null).statusCode()).isEqualTo(200);
        assertThat(call(platformToken,"PUT",path,"{\"name\":\"Platform status fixture\",\"description\":\"Fixture\",\"status\":\"Active\"}").statusCode()).isEqualTo(200);
        assertThat(sql.queryForObject("SELECT Status FROM Partner.Organization WHERE OrganizationId=?",Integer.class,org.getId())).isEqualTo(1);
    }
    @Test void tenantCannotRevokeOrganizationThroughDetailsUpdate() throws Exception {
        String body="{\"name\":\"Policy A\",\"description\":\"Fixture\",\"status\":\"Revoked\"}";
        int status;
        int persistedStatus;
        try {
            status=call(adminToken,"PUT","/api/v1/organizations/"+orgA.getRowGuid(),body).statusCode();
            persistedStatus=sql.queryForObject("SELECT Status FROM Partner.Organization WHERE OrganizationId=?",Integer.class,orgA.getId());
        } finally { sql.update("UPDATE Partner.Organization SET Status=1 WHERE OrganizationId=?",orgA.getId()); }
        assertThat(status).isEqualTo(403);
        assertThat(persistedStatus).isEqualTo(1);
    }
}
