package com.chellavignesh.authserver.recovery;

import com.chellavignesh.authserver.adminportal.application.*;
import com.chellavignesh.authserver.adminportal.application.dto.*;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.forgotusername.ForgotUsernameSetting;
import com.chellavignesh.authserver.adminportal.credential.CredentialService;
import com.chellavignesh.authserver.adminportal.credential.CredentialRepository;
import com.chellavignesh.authserver.adminportal.credential.entity.Credential;
import com.chellavignesh.authserver.adminportal.credential.dto.CredentialMetadataDto;
import com.chellavignesh.authserver.adminportal.credential.dto.CredentialResponseDto;
import com.chellavignesh.authserver.adminportal.credential.secret.SecretService;
import com.chellavignesh.authserver.adminportal.certificate.OrganizationCertificateService;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import com.chellavignesh.authserver.adminportal.credential.CredentialStatus;
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
@ExtendWith(OutputCaptureExtension.class)
class CredentialMetadataIntegrationTest {
    @org.springframework.beans.factory.annotation.Value("${server.base-path}") String issuer;
    static final String database="AuthserverCredentials_"+UUID.randomUUID().toString().replace("-","");
    static final String namespace="authserver:credentials:"+UUID.randomUUID()+":";
    static JdbcTemplate admin;
    static boolean owned;
    @DynamicPropertySource static void isolate(DynamicPropertyRegistry registry) {
        String url=System.getenv("AUTH_DB_IT_URL");
        if (url==null || !url.matches("jdbc:sqlserver://(?:127\\.0\\.0\\.1|localhost):15433;encrypt=true;trustServerCertificate=true"))
            throw new IllegalStateException("Credential tests require the approved loopback SQL Server");
        SQLServerDataSource ds=new SQLServerDataSource(); ds.setURL(url);
        ds.setUser(System.getenv("AUTH_DB_IT_USER")); ds.setPassword(System.getenv("AUTH_DB_IT_PASSWORD"));
        admin=new JdbcTemplate(ds); admin.execute("CREATE DATABASE ["+database+"]"); owned=true;
        Runtime.getRuntime().addShutdownHook(new Thread(CredentialMetadataIntegrationTest::dropOwnedDatabase));
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
    @Autowired CredentialRepository credentialRepository;
    @Autowired SecretService secretService;
    @Autowired OrganizationCertificateService certificateService;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired @Qualifier("redisTemplate") RedisTemplate<String,Object> sessionRedis;
    @Autowired @Qualifier("cacheRedisTemplate") RedisTemplate<String,Object> cacheRedis;
    @LocalServerPort int port;
    Organization orgA,orgB;

    Application app;
    Fixture adminToken,viewerToken;
    final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    record Fixture(String value,byte[] key) {}
    @BeforeAll void fixtures() throws Exception {
        orgA=organization("Credentials A"); orgB=organization("Credentials B");
        int adminGroup=group(orgA,"Credential administrator",List.of("idp-admin-app:read","idp-admin-app:update"));
        int viewerGroup=group(orgA,"Viewer",List.of("idp-admin-app:read"));
        User admin=user(orgA,adminGroup,"credential-admin");
        User viewer=user(orgA,viewerGroup,"credential-viewer");
        app=application(orgA);
        redirects.createRedirectUris(orgA.getId(),app.getId(),List.of("http://localhost/callback"));
        adminToken=token(admin);viewerToken=token(viewer);
    }
    @AfterAll void cleanup() {
        for (RedisTemplate<String,Object> redis:List.of(sessionRedis,cacheRedis)) {
            // The literal UUID namespace owns every key selected here; never flush a Redis DB.
            Set<String> keys=redis.keys(namespace+"*");
            if(keys!=null && !keys.isEmpty()) redis.delete(keys);
        }
        dropOwnedDatabase();
    }
    Organization organization(String name) throws Exception {var dto=new CreateOrganizationDto();dto.setName(name);dto.setDescription("Disposable credential fixture");return organizations.create(dto);}
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

    Application confidentialApplication() throws Exception {
        var dto=new CreateApplicationDto();dto.setName("Credential "+UUID.randomUUID());dto.setDescription("Owned fixture");dto.setType(ApplicationTypeEnum.SERVER);dto.setAuthMethod(AuthFlowEnum.CLIENT_SECRET_JWT);dto.setUsernameType(UsernameTypeEnum.USERNAME);dto.setAllowForgotUsername(false);
        var settings=new UpdateTokenSettingsDto();settings.setAuthCodeTimeToLive(120);settings.setAccessTokenTimeToLive(600);settings.setRefreshTokenTimeToLive(3600);settings.setDeviceCodeTimeToLive(120);settings.setMaxRequestTransitTime(30);settings.setReuseRefreshTokens(false);
        return applications.create(orgA.getId(),dto,settings,new ForgotUsernameSetting(List.of()),AccessTokenFormatEnum.REFERENCE);
    }
    String path(Application client) {return "/api/v1/organizations/"+orgA.getRowGuid()+"/applications/"+client.getRowGuid()+"/credentials";}
    HttpResponse<String> create(Application client,String name) throws Exception {
        return call(adminToken,"POST",path(client),mapper.writeValueAsString(Map.of("name",name,"type","SHARED_SECRET","algorithm","HS256","expireOn","2036-06-01T18:00:00Z")));
    }
    JsonNode created(Application client,String name) throws Exception {
        var response=create(client,name);assertThat(response.statusCode()).isEqualTo(201);return mapper.readTree(response.body());
    }
    @Test void viewerCannotReadSecretMaterial() throws Exception {
        var client=confidentialApplication();var creation=created(client,"viewer secret");String secret=creation.path("secretValue").asText();
        for(String route:List.of(path(client),path(client).replace("/v1/","/v2/"))) {
            var response=call(viewerToken,"GET",route,null);
            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode item=mapper.readTree(response.body()).get(0);
            assertThat(item.has("value")||item.has("secretValue")||item.has("secret")).isFalse();
            assertThat(response.body().contains(secret)).isFalse();
            assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
            if(route.contains("/v2/")) assertThat(item.properties().stream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("id","name","type","algorithm","status","expirationDate","isExpired");
        }
        assertThat(credentials.getAll(orgA.getId(),client.getId()).getFirst().getValue()).isNull();
    }
    @Test void secretOnlyReturnedOnCreation() throws Exception {
        var client=confidentialApplication();var response=create(client,"one time");assertThat(response.statusCode()).isEqualTo(201);
        assertThat(mapper.readTree(response.body()).properties().stream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("id","secretValue");
        assertThat(mapper.readTree(response.body()).path("secretValue").asText().isBlank()).isFalse();
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(call(viewerToken,"POST",path(client),"{\"name\":\"denied\",\"type\":\"SHARED_SECRET\",\"algorithm\":\"HS256\"}").statusCode()).isEqualTo(403);
        var update=call(adminToken,"PUT",path(client)+"/"+mapper.readTree(response.body()).path("id").asText(),"{\"status\":\"Disabled\"}");
        assertThat(update.statusCode()).isEqualTo(200);assertThat(update.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }
    @Test void expiryUsesUtcInstant() throws Exception {
        var client=confidentialApplication();var item=created(client,"boundary");
        sql.update("UPDATE Client.Credential SET ExpireOn=? WHERE RowGuid=?","2035-06-01T18:00:00",item.path("id").asText());
        var before=new CredentialService(credentialRepository,secretService,certificateService,applications,organizationRepository,
                Clock.fixed(Instant.parse("2035-06-01T12:00:00Z"),ZoneId.of("Asia/Kolkata")));
        var atBoundary=new CredentialService(credentialRepository,secretService,certificateService,applications,organizationRepository,
                Clock.fixed(Instant.parse("2035-06-01T18:00:00Z"),ZoneId.of("America/Los_Angeles")));
        assertThat(before.listMetadata(orgA.getRowGuid(),client.getRowGuid()).getFirst().isExpired()).isFalse();
        assertThat(atBoundary.listMetadata(orgA.getRowGuid(),client.getRowGuid()).getFirst().isExpired()).isTrue();
        var credential=credentialRepository.getAll(orgA.getId(),client.getId()).getFirst();
        assertThat(CredentialResponseDto.fromCredential(credential,before.isCredentialExpired(credential)).isExpired()).isFalse();
        assertThat(CredentialResponseDto.fromCredential(credential,atBoundary.isCredentialExpired(credential)).isExpired()).isTrue();
        credential.setExpireOn("2035-06-01T19:00:00+01:00");assertThat(atBoundary.isCredentialExpired(credential)).isTrue();
        credential.setExpireOn("2035-06-01 18:00:00");assertThat(atBoundary.isCredentialExpired(credential)).isTrue();
        credential.setExpireOn("2035-06-01");assertThat(atBoundary.isCredentialExpired(credential)).isTrue();
        credential.setExpireOn(null);assertThat(atBoundary.isCredentialExpired(credential)).isFalse();
        for(String route:List.of(path(client),path(client).replace("/v1/","/v2/"))) {
            var response=call(viewerToken,"GET",route,null);assertThat(response.statusCode()).isEqualTo(200);
            var metadata=mapper.readTree(response.body()).get(0);assertThat(metadata.path("isExpired").asBoolean()).isFalse();
            assertThat(Instant.parse(metadata.path("expirationDate").asText())).isEqualTo(Instant.parse("2035-06-01T18:00:00Z"));
        }
    }
    @Test void malformedExpiryFailsClosedWithout500(CapturedOutput output) throws Exception {
        var credential=new Credential();credential.setRowGuid(UUID.randomUUID());credential.setName("invalid imported expiry");credential.setTokenAlgorithm(AlgorithmEnum.HS256);credential.setAuthFlow(AuthFlowEnum.CLIENT_SECRET_JWT);credential.setCredentialStatus(CredentialStatus.Active);credential.setExpireOn("malformed-import");
        assertThat(credentials.isCredentialExpired(credential)).isTrue();
        assertThat(CredentialResponseDto.fromCredential(credential).isExpired()).isTrue();
        var metadata=CredentialMetadataDto.fromCredential(credential,credentials.isCredentialExpired(credential));
        assertThat(mapper.readTree(mapper.writeValueAsString(metadata)).path("isExpired").asBoolean()).isTrue();
        assertThat(output.getOut()).contains("Invalid credential expiration").doesNotContain("malformed-import","DateTimeParseException");
    }
    HttpResponse<String> issue(Application client,String secret) throws Exception {
        Instant now=Instant.now();
        var jwt=new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer(client.getClientId()).subject(client.getClientId()).audience(issuer+"/oauth2/token").issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(60))).jwtID(UUID.randomUUID().toString()).build());
        jwt.sign(new com.nimbusds.jose.crypto.MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        var fields=Map.of("grant_type","client_credentials","scope","confidential-client","client_id",client.getClientId(),"client_assertion_type","urn:ietf:params:oauth:client-assertion-type:jwt-bearer","client_assertion",jwt.serialize());
        String body=fields.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/services/oauth2/token")).header("User-Agent","PolicyIntegration/1.0").header("Accept-Language","en-US").header("x-request-datetime",OffsetDateTime.now(ZoneOffset.UTC).toString()).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request,HttpResponse.BodyHandlers.ofString());
    }
    @Test void confidentialJwtStillAuthenticates() throws Exception {
        var client=confidentialApplication();var first=created(client,"old");var second=created(client,"new");
        assertThat(issue(client,first.path("secretValue").asText()).statusCode()).isEqualTo(200);
        assertThat(issue(client,second.path("secretValue").asText()).statusCode()).isEqualTo(200);
        assertThat(create(client,"third").statusCode()).isEqualTo(409);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Client.Credential WHERE ApplicationId=?",Integer.class,client.getId())).isEqualTo(2);
        String oldPath=path(client)+"/"+first.path("id").asText();
        assertThat(call(adminToken,"DELETE",oldPath,null).statusCode()).isEqualTo(400);
        assertThat(call(adminToken,"PUT",oldPath,"{\"status\":\"Disabled\"}").statusCode()).isEqualTo(200);
        assertThat(issue(client,first.path("secretValue").asText()).statusCode()).isIn(400,401);
        assertThat(issue(client,second.path("secretValue").asText()).statusCode()).isEqualTo(200);
        var deleted=call(adminToken,"DELETE",oldPath,null);assertThat(deleted.statusCode()).isEqualTo(204);assertThat(deleted.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(sql.queryForObject("SELECT CredentialStatus FROM Client.Credential WHERE RowGuid=?",Integer.class,first.path("id").asText())).isEqualTo(CredentialStatus.Inactive.getValue());
    }
    @Test void foreignCredentialMetadataIsDenied() throws Exception {
        var client=confidentialApplication();created(client,"foreign denial");
        var stranger=user(orgB,group(orgB,"Foreign viewer",List.of("idp-admin-app:read")),"credential-stranger");
        for(String route:List.of(path(client),path(client).replace("/v1/","/v2/"))) {
            assertThat(call(token(stranger),"GET",route,null).statusCode()).isEqualTo(403);
            assertThat(call(null,"GET",route,null).statusCode()).isEqualTo(401);
        }
        var foreignApp=application(orgB);
        String forged=path(client).replace("/v1/","/v2/").replace(client.getRowGuid().toString(),foreignApp.getRowGuid().toString());
        var denied=call(viewerToken,"GET",forged,null);
        assertThat(denied.statusCode()).isEqualTo(404);
        var problem=mapper.readTree(denied.body());assertThat(problem.path("code").asText()).isEqualTo("NOT_FOUND");
        assertThat(UUID.fromString(problem.path("requestId").asText())).isNotNull();
        assertThat(problem.path("retryable").asBoolean()).isFalse();
        assertThat(denied.body()).doesNotContain("Exception",foreignApp.getRowGuid().toString());
    }
}
