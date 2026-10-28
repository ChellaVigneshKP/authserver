package com.chellavignesh.authserver.recovery;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.credential.CredentialService;
import com.chellavignesh.authserver.adminportal.credential.secret.SecretService;
import com.chellavignesh.authserver.support.ManagementHttpTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.*;

import java.math.BigInteger;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import org.mockito.Mockito;

/** Real HTTP and SQL lifecycle assertions;
every row belongs to this UUID database. */
@EnabledIfEnvironmentVariable(named="AUTH_APP_IT", matches="true")
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "app.local.bootstrap.enabled=false", "toggles.signature.required=true",
        "logging.level.root=WARN", "logback.access.enabled=false"})
class AdminLifecycleIntegrationTest extends ManagementHttpTestSupport {
    static final OwnedDatabase database = new OwnedDatabase();
    @DynamicPropertySource static void isolate(DynamicPropertyRegistry registry) { database.register(registry); }
    @Autowired CredentialService credentials;
    @Autowired SecretService secrets;
    @Autowired ApplicationService service;
    @Autowired TestClock clock;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.chellavignesh.authserver.security.ManagementMutationLock mutationLock;
    @Value("${server.base-path}") String issuer;
    Fixture admin, platform;
    KeyPair keys;

    static final class TestClock extends Clock {
        volatile Instant fixed;
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Kolkata"); }
        @Override public Clock withZone(ZoneId zone) { return fixed == null ? Clock.system(zone) : Clock.fixed(fixed, zone); }
        @Override public Instant instant() { return fixed == null ? Instant.now() : fixed; }
    }
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary TestClock lifecycleClock() { return new TestClock(); }
    }
    @BeforeAll
    void fixtures() throws Exception {
        orgA = organization("Lifecycle A");
        orgB = organization("Lifecycle B");
        int group = group(orgA,"Lifecycle administrator", List.of("idp-admin-app:read","idp-admin-app:create","idp-admin-app:update","idp-admin-app:delete",
                "idp-admin-cred-cert:create","idp-admin-cred-cert:read","idp-admin-cred-cert:delete"));
        app = application(orgA);
        redirects.createRedirectUris(orgA.getId(),app.getId(),List.of("http://localhost/callback"));
        admin = token(user(orgA,group,"lifecycle-admin"));
        platform = token(user(organizations.getById(1).orElseThrow(),1,"lifecycle-platform"));
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
    }
    @AfterEach
    void restoreTime() { clock.fixed = null; }
    @AfterAll
    void cleanup() { cleanup(database); }
    String root() { return "/api/v1/organizations/"+orgA.getRowGuid()+"/applications"; }
    String path(Application client) { return root()+"/"+client.getRowGuid(); }
    int appCount() { return sql.queryForObject("SELECT COUNT(*) FROM Client.Application WHERE OrganizationId=?",Integer.class,orgA.getId()); }
    JsonNode create(String type,String auth) throws Exception {
        var response=call(admin,"POST",root(),mapper.writeValueAsString(Map.of("name","Lifecycle "+UUID.randomUUID(),"description","Owned fixture","type",type,"authMethod",auth)));
        assertThat(response.statusCode()).isEqualTo(201);
        return mapper.readTree(response.body());
    }
    Application confidential() throws Exception { return applications.get(UUID.fromString(create("SERVER","CLIENT_SECRET_JWT").path("id").asText())).orElseThrow(); }
    JsonNode credential(Application client) throws Exception {
        var response=call(admin,"POST",path(client)+"/credentials","{\"name\":\"Owned secret\",\"type\":\"SHARED_SECRET\",\"algorithm\":\"HS256\"}");
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        return mapper.readTree(response.body());
    }
    HttpResponse<String> issue(Application client,String secret) throws Exception {
        Instant now=Instant.now();
        var jwt=new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),new com.nimbusds.jwt.JWTClaimsSet.Builder()
                .issuer(client.getClientId()).subject(client.getClientId()).audience(issuer+"/oauth2/token").issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(60))).jwtID(UUID.randomUUID().toString()).build());
        jwt.sign(new com.nimbusds.jose.crypto.MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        var fields=Map.of("grant_type","client_credentials","scope","confidential-client","client_id",client.getClientId(),"client_assertion_type","urn:ietf:params:oauth:client-assertion-type:jwt-bearer","client_assertion",jwt.serialize());
        String body=fields.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/services/oauth2/token"))
                .header("User-Agent","PolicyIntegration/1.0").header("Accept-Language","en-US").header("x-request-datetime",clock.instant().toString())
                .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> introspect(Fixture bearer) throws Exception {
        var fields=Map.of("token",bearer.value(),"res_url",Base64.getEncoder().encodeToString("https://example.test/lifecycle".getBytes(StandardCharsets.UTF_8)),"res_method","GET");
        String body=fields.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(bearer.key(),"HmacSHA256"));
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/services/oauth2/introspect"))
                .header("Authorization","Bearer "+bearer.value()).header("x-signature",Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8))))
                .header("User-Agent","PolicyIntegration/1.0").header("Accept-Language","en-US").header("x-request-datetime",clock.instant().toString())
                .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    int tokens(Application client) { return sql.queryForObject("SELECT COUNT(*) FROM Token.Token WHERE ApplicationId=?",Integer.class,client.getId()); }
    void rejectedIssuance(Application client,String secret) throws Exception {
        int before=tokens(client);
        var response=issue(client,secret);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(mapper.readTree(response.body()).path("error").asText()).isEqualTo("invalid_client");
        assertThat(tokens(client)).isEqualTo(before);
    }
    @Test
    void serverPkceRejectedWithoutSqlMutation() throws Exception {
        int before=appCount();
        var response=call(admin,"POST",root(),"{\"name\":\"Invalid server\",\"type\":\"SERVER\",\"authMethod\":\"PKCE\"}");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(appCount()).isEqualTo(before);
    }
    @Test
    void summariesAndDetailsExposeActualActiveState() throws Exception {
        var creation=create("WEB","PKCE");
        String id=creation.path("id").asText();
        assertThat(creation.path("active").isBoolean()).isTrue();
        assertThat(creation.path("active").asBoolean()).isTrue();
        var detail=mapper.readTree(call(admin,"GET",root()+"/"+id,null).body());
        assertThat(detail.path("active").asBoolean()).isTrue();
        assertThat(call(admin,"DELETE",root()+"/"+id,null).statusCode()).isEqualTo(200);
        detail=mapper.readTree(call(admin,"GET",root()+"/"+id,null).body());
        assertThat(detail.path("active").isBoolean()).isTrue();
        assertThat(detail.path("active").asBoolean()).isFalse();
        assertThat(mapper.readTree(call(admin,"GET",root(),null).body()).findValuesAsText("id")).doesNotContain(id);
        assertThat(call(admin,"PUT",root()+"/"+id,"{\"name\":\"No reactivation\",\"active\":true}").statusCode()).isEqualTo(400);
        assertThat(sql.queryForObject("SELECT Active FROM Client.Application WHERE RowGuid=?",Boolean.class,id)).isFalse();
    }
    @Test
    void partialOnboardingReadbackAndImmutableFields() throws Exception {
        var creation=create("WEB","PKCE");
        String route=root()+"/"+creation.path("id").asText();
        int count=appCount();
        // The next onboarding step fails;
        // Read back this created client instead of retrying creation.
        assertThat(call(admin,"PUT",route+"/urls",urlBody(List.of("javascript:invalid"))).statusCode()).isEqualTo(400);
        var urls=mapper.readTree(call(admin,"GET",route+"/urls",null).body());
        assertThat(urls.path("redirectUrls").size()).isZero();
        assertThat(urls.path("logoutRedirectUrls").size()).isZero();
        assertThat(mapper.readTree(call(admin,"GET",route+"/credentials",null).body()).size()).isZero();
        assertThat(call(admin,"PUT",route,"{\"name\":\"Renamed lifecycle\",\"type\":\"SERVER\",\"authMethod\":\"PRIVATE_KEY_JWT\",\"authenticationMethod\":\"PRIVATE_KEY_JWT\"}").statusCode()).isEqualTo(200);
        var detail=mapper.readTree(call(admin,"GET",route,null).body());
        assertThat(detail.path("type").asText()).isEqualTo("WEB");
        assertThat(detail.path("authenticationMethod").asText()).isEqualTo("PKCE");
        assertThat(appCount()).isEqualTo(count);
        var persisted=applications.get(UUID.fromString(creation.path("id").asText())).orElseThrow();
        assertThat(persisted.getType().name()).isEqualTo("WEB");
        assertThat(persisted.getAuthFlow().name()).isEqualTo("PKCE");
    }
    String urlBody(List<String> redirects) throws Exception { return mapper.writeValueAsString(Map.of("applicationUrl","http://localhost:3000/","returnUrls",redirects,"logoutUrls",List.of("http://localhost:3000/logout")));
    }
    @Test
    void duplicateAndInvalidUrlsRejectedBeforeAnySqlChanges() throws Exception {
        var client=confidential();
        String route=path(client)+"/urls";
        String before=call(admin,"GET",route,null).body();
        for(var urls:List.of(List.of("http://localhost/callback","http://localhost/callback"),List.of("javascript:alert(1)"))) {
            assertThat(call(admin,"PUT",route,urlBody(urls)).statusCode()).isEqualTo(400);
            assertThat(call(admin,"GET",route,null).body()).isEqualTo(before);
            assertThat(service.getApplicationRedirectUris(client.getId())).isEmpty();
            assertThat(applications.getById(client.getId()).orElseThrow().getUri()).isEmpty();
        }
        assertThat(call(admin,"PUT",route,urlBody(List.of("http://localhost:3000/callback"))).statusCode()).isEqualTo(200);
        assertThat(service.getApplicationRedirectUris(client.getId())).containsExactly("http://localhost:3000/callback");
    }
    @Test
    void duplicateClientNameDoesNotCreateAnotherClient() throws Exception {
        var created=create("WEB","PKCE");
        int count=appCount();
        assertThat(call(admin,"POST",root(),mapper.writeValueAsString(Map.of("name",created.path("name").asText(),"type","WEB","authMethod","PKCE"))).statusCode()).isEqualTo(409);
        assertThat(appCount()).isEqualTo(count);
    }
    @Test
    void httpRequestedUtcExpiryPersistsAndBoundsActualIssuance() throws Exception {
        var client=confidential();
        Instant boundary=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(10);
        var response=call(admin,"POST",path(client)+"/credentials",mapper.writeValueAsString(Map.of("name","UTC request", "type","SHARED_SECRET", "algorithm","HS256", "expireOn",boundary.toString())));
        assertThat(response.statusCode()).isEqualTo(201);
        var created=mapper.readTree(response.body());
        String id=created.path("id").asText();
        String secret=created.path("secretValue").asText();
        var utc=LocalDateTime.ofInstant(boundary,ZoneOffset.UTC);
        assertThat(sql.queryForObject("SELECT ExpireOn FROM Client.Credential WHERE RowGuid=?",LocalDateTime.class,id)).isEqualTo(utc);
        assertThat(sql.queryForObject("SELECT s.ExpireOn FROM Client.Secret s JOIN Client.Credential c ON c.SecretId=s.SecretId WHERE c.RowGuid=?",LocalDateTime.class,id)).isEqualTo(utc);
        clock.fixed=boundary.minusNanos(100);
        assertThat(issue(client,secret).statusCode()).isEqualTo(200);
        clock.fixed=boundary;
        rejectedIssuance(client,secret);
        clock.fixed=boundary.plusNanos(100);
        rejectedIssuance(client,secret);
        for(String version:List.of("v1","v2")) {
            var read=call(admin,"GET",path(client).replace("v1",version)+"/credentials",null);
            assertThat(read.statusCode()).isEqualTo(200);
            var metadata=mapper.readTree(read.body()).get(0);
            assertThat(Instant.parse(metadata.path("expirationDate").asText())).isEqualTo(boundary);
            assertThat(metadata.path("isExpired").asBoolean()).isTrue();
            assertThat(read.body()).doesNotContain(secret);
        }
    }
    @Test
    void actualConfidentialIssuanceBeforeAtAndAfterExactSameDayExpiry() throws Exception {
        var client=confidential();
        var created=credential(client);
        String secret=created.path("secretValue").asText();
        String id=created.path("id").asText();
        Instant boundary=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(10);
        var utc=LocalDateTime.ofInstant(boundary,ZoneOffset.UTC);
        sql.update("UPDATE Client.Credential SET ExpireOn=? WHERE RowGuid=?",utc,id);
        sql.update("UPDATE s SET ExpireOn=? FROM Client.Secret s JOIN Client.Credential c ON c.SecretId=s.SecretId WHERE c.RowGuid=?",utc,id);
        clock.fixed=boundary.minusNanos(100);
        var issued=issue(client,secret);
        assertThat(issued.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(issued.body()).path("access_token").asText()).isNotBlank();
        clock.fixed=boundary;
        rejectedIssuance(client,secret);
        clock.fixed=boundary.plusNanos(100);
        rejectedIssuance(client,secret);
    }
    @Test
    void credentialEarlierExpiryRestrictsActualIssuance() throws Exception {
        var client=confidential();
        var created=credential(client);
        String secret=created.path("secretValue").asText();
        Instant boundary=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(10);
        sql.update("UPDATE Client.Credential SET ExpireOn=? WHERE RowGuid=?",LocalDateTime.ofInstant(boundary,ZoneOffset.UTC),created.path("id").asText());
        clock.fixed=boundary.minusNanos(100);
        assertThat(issue(client,secret).statusCode()).isEqualTo(200);
        clock.fixed=boundary;
        rejectedIssuance(client,secret);
        clock.fixed=boundary.plusNanos(100);
        rejectedIssuance(client,secret);
    }
    @Test
    void secretEarlierExpiryRestrictsActualIssuanceAndMetadata() throws Exception {
        var client=confidential();
        var created=credential(client);
        String secret=created.path("secretValue").asText();
        Instant boundary=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(10);
        sql.update("UPDATE s SET ExpireOn=? FROM Client.Secret s JOIN Client.Credential c ON c.SecretId=s.SecretId WHERE c.RowGuid=?",LocalDateTime.ofInstant(boundary,ZoneOffset.UTC),created.path("id").asText());
        clock.fixed=boundary.minusNanos(100);
        assertThat(issue(client,secret).statusCode()).isEqualTo(200);
        clock.fixed=boundary;
        rejectedIssuance(client,secret);
        clock.fixed=boundary.plusNanos(100);
        rejectedIssuance(client,secret);
        for(String version:List.of("v1","v2")) {
            var response=call(admin,"GET",path(client).replace("v1",version)+"/credentials",null);
            assertThat(response.statusCode()).isEqualTo(200);
            var metadata=mapper.readTree(response.body()).get(0);
            assertThat(Instant.parse(metadata.path("expirationDate").asText())).isEqualTo(boundary);
            assertThat(metadata.path("isExpired").asBoolean()).isTrue();
            assertThat(response.body()).doesNotContain(secret);
        }
    }
    @Test
    void singleRemainingExpiryBoundsActualIssuance() throws Exception {
        for(boolean credentialNull:List.of(true,false)) {
            var client=confidential();
            var created=credential(client);
            String id=created.path("id").asText();
            String secret=created.path("secretValue").asText();
            Instant boundary=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(10);
            var utc=LocalDateTime.ofInstant(boundary,ZoneOffset.UTC);
            sql.update("UPDATE Client.Credential SET ExpireOn=? WHERE RowGuid=?",credentialNull?null:utc,id);
            sql.update("UPDATE s SET ExpireOn=? FROM Client.Secret s JOIN Client.Credential c ON c.SecretId=s.SecretId WHERE c.RowGuid=?",credentialNull?utc:null,id);
            clock.fixed=boundary.minusNanos(100);
            assertThat(issue(client,secret).statusCode()).isEqualTo(200);
            clock.fixed=boundary;
            rejectedIssuance(client,secret);
        }
    }
    @Test
    void missingBothExpiriesDoesNotPermitConfidentialIssuance() throws Exception {
        var client=confidential();
        var created=credential(client);
        String id=created.path("id").asText();
        sql.update("UPDATE Client.Credential SET ExpireOn=NULL WHERE RowGuid=?",id);
        sql.update("UPDATE s SET ExpireOn=NULL FROM Client.Secret s JOIN Client.Credential c ON c.SecretId=s.SecretId WHERE c.RowGuid=?",id);
        rejectedIssuance(client,created.path("secretValue").asText());
    }
    @Test
    void disabledCredentialRejectsIssuanceWithoutTokenRows() throws Exception {
        var client=confidential();
        var created=credential(client);
        String secret=created.path("secretValue").asText();
        assertThat(issue(client,secret).statusCode()).isEqualTo(200);
        assertThat(call(admin,"PUT",path(client)+"/credentials/"+created.path("id").asText(),"{\"status\":\"Disabled\"}").statusCode()).isEqualTo(200);
        rejectedIssuance(client,secret);
        assertThat(sql.queryForObject("SELECT CredentialStatus FROM Client.Credential WHERE RowGuid=?",Integer.class,created.path("id").asText())).isEqualTo(com.chellavignesh.authserver.adminportal.credential.CredentialStatus.Disabled.getValue());
    }
    @Test
    void clientDeactivationRejectsExistingBearerAndNewIssuance() throws Exception {
        var client=confidential();
        var created=credential(client);
        String secret=created.path("secretValue").asText();
        var issued=mapper.readTree(issue(client,secret).body());
        var bearer=issued.path("access_token").asText();
        var retained=new Fixture(bearer,issued.path("signing_key").binaryValue());
        var active=introspect(retained);
        assertThat(active.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(active.body()).path("active").asBoolean()).isTrue();
        assertThat(bearer).isNotBlank();
        assertThat(call(admin,"DELETE",path(client),null).statusCode()).isEqualTo(200);
        assertThat(introspect(retained).statusCode()).isEqualTo(401);
        rejectedIssuance(client,secret);
        assertThat(applications.getById(client.getId()).orElseThrow().getActive()).isFalse();
    }
    @Test
    void organizationRevocationRejectsExistingBearerAndNewIssuance() throws Exception {
        var client=confidential();
        var created=credential(client);
        String secret=created.path("secretValue").asText();
        var issued=mapper.readTree(issue(client,secret).body());
        var bearer=issued.path("access_token").asText();
        var retained=new Fixture(bearer,issued.path("signing_key").binaryValue());
        var active=introspect(retained);
        assertThat(active.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(active.body()).path("active").asBoolean()).isTrue();
        int previous=sql.queryForObject("SELECT Status FROM Partner.Organization WHERE OrganizationId=?",Integer.class,orgA.getId());
        try {
            assertThat(call(platform,"PUT","/api/v1/organizations/"+orgA.getRowGuid(),"{\"name\":\"Lifecycle revoked\",\"description\":\"Owned fixture\",\"status\":\"Revoked\"}").statusCode()).isEqualTo(200);
            assertThat(sql.queryForObject("SELECT Status FROM Partner.Organization WHERE OrganizationId=?",Integer.class,orgA.getId())).isZero();
            assertThat(introspect(retained).statusCode()).isEqualTo(401);
            rejectedIssuance(client,secret);
            assertThat(call(admin,"POST",root(),"{\"name\":\"Denied revoked\",\"type\":\"WEB\",\"authMethod\":\"PKCE\"}").statusCode()).isEqualTo(401);
        } finally { sql.update("UPDATE Partner.Organization SET Status=? WHERE OrganizationId=?",previous,orgA.getId()); }
    }
    byte[] pem(boolean expired,boolean privateKey) throws Exception {
        Instant now=Instant.now();
        var name=new X500Name("CN=Owned Lifecycle");
        var cert=new JcaX509v3CertificateBuilder(name,new BigInteger(120,new SecureRandom()),Date.from(now.minusSeconds(3600)),Date.from(now.plusSeconds(expired?-60:86400)),name,keys.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate()));
        var writer=new java.io.StringWriter();
        try(var out=new org.bouncycastle.util.io.pem.PemWriter(writer)) {
            out.writeObject(new org.bouncycastle.util.io.pem.PemObject("CERTIFICATE",cert.getEncoded()));
            if(privateKey) out.writeObject(new org.bouncycastle.util.io.pem.PemObject("PRIVATE KEY",keys.getPrivate().getEncoded()));
        }return writer.toString().getBytes(StandardCharsets.US_ASCII);
    }
    HttpResponse<String> upload(byte[] bytes,String type) throws Exception {
        return upload(bytes, type, type.equals("ORGANIZATION") ? platform : admin, orgA.getRowGuid());
    }
    HttpResponse<String> upload(byte[] bytes,String type,Fixture actor,UUID organization) throws Exception {
        String boundary="owned"+UUID.randomUUID();
        var body=new java.io.ByteArrayOutputStream();
        for(var entry:Map.of("name","Owned certificate","type",type,"password","").entrySet()) body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+entry.getKey()+"\"\r\n\r\n"+entry.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"certificate\"; filename=\"fixture.pem\"\r\nContent-Type: application/x-pem-file\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(bytes);
        body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        return observed(type.equals("ORGANIZATION") ? "human_platform_organization" : "human_tenant", "POST", "/api/v1/organizations/"+organization+"/certificates", () -> http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/services/api/v1/organizations/"+organization+"/certificates"))
                .header("Authorization","Bearer "+actor.value()).header("User-Agent","PolicyIntegration/1.0").header("Accept-Language","en-US").header("x-request-datetime",Instant.now().toString())
                .header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofString()));
    }
    int certificates() { return sql.queryForObject("SELECT COUNT(*) FROM Partner.Certificate WHERE OrganizationId=?",Integer.class,orgA.getId()); }
    @Test
    void internalSecretMappingPreservesFullSqlUtcTime() throws Exception {
        var client=confidential();
        var created=credential(client);
        Instant boundary=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(10);
        var utc=LocalDateTime.ofInstant(boundary,ZoneOffset.UTC);
        String id=created.path("id").asText();
        sql.update("UPDATE Client.Credential SET ExpireOn=? WHERE RowGuid=?",utc,id);
        sql.update("UPDATE s SET ExpireOn=? FROM Client.Secret s JOIN Client.Credential c ON c.SecretId=s.SecretId WHERE c.RowGuid=?",utc,id);
        var internal=credentials.getAllClientSecretJwt(orgA.getId(),client.getId()).getFirst();
        assertThat(Instant.parse(internal.getExpireOn())).isEqualTo(boundary);
        assertThat(secrets.getById(internal.getSecretId()).orElseThrow().getExpiration()).isEqualTo(boundary.toString());
    }
    @Test
    void organizationCertificateRequiresPrivateKeyWithoutSqlMutation() throws Exception {
        int before=certificates();
        assertThat(upload(pem(false,false),"ORGANIZATION").statusCode()).isEqualTo(400);
        assertThat(certificates()).isEqualTo(before);
    }
    @Test
    void certificateRequestValidationRejectsOversizedFilesWithoutContainer() {
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var request=new com.chellavignesh.authserver.adminportal.certificate.dto.CreateCertificateRequest("Owned", "", "PUBLIC_KEY",
                    new org.springframework.mock.web.MockMultipartFile("certificate",new byte[1024*1024+1]));
            assertThat(factory.getValidator().validate(request)).isNotEmpty();
            var boundary=new com.chellavignesh.authserver.adminportal.certificate.dto.CreateCertificateRequest("Owned", "", "PUBLIC_KEY",
                    new org.springframework.mock.web.MockMultipartFile("certificate",new byte[1024*1024]));
            assertThat(factory.getValidator().validate(boundary)).isEmpty();
        }
    }
    @Test
    void malformedPemRejectedWithoutSqlMutation() throws Exception {
        int before=certificates();
        assertThat(upload("-----BEGIN CERTIFICATE-----\nmalformed\n-----END CERTIFICATE-----".getBytes(StandardCharsets.US_ASCII),"PUBLIC_KEY").statusCode()).isEqualTo(400);
        assertThat(certificates()).isEqualTo(before);
    }
    @Test
    void expiredCertificateRejectedWithoutSqlMutation() throws Exception {
        int before=certificates();
        assertThat(upload(pem(true,false),"PUBLIC_KEY").statusCode()).isEqualTo(400);
        assertThat(certificates()).isEqualTo(before);
    }
    @Test
    void oversizedPemRejectedWithoutSqlMutation() throws Exception {
        int before=certificates();
        byte[] valid=pem(false,false);
        byte[] large=Arrays.copyOf(valid,1024*1024+1);
        Arrays.fill(large,valid.length,large.length,(byte)' ');
        var denied=upload(large,"PUBLIC_KEY");
        assertThat(denied.statusCode()).isEqualTo(413);
        assertThat(certificates()).isEqualTo(before);
        assertThat(denied.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(mapper.readTree(denied.body()).path("code").asText()).isEqualTo("UPLOAD_TOO_LARGE");
        assertThat(upload(Arrays.copyOf(large,1024*1024),"PUBLIC_KEY").statusCode()).isEqualTo(201);
    }
    @Test
    void certificateDerivedCredentialExpiryPreservesSqlUtcTime() throws Exception {
        var certificate=upload(pem(false,false),"PUBLIC_KEY");
        assertThat(certificate.statusCode()).isEqualTo(201);
        String certificateId=mapper.readTree(certificate.body()).path("id").asText();
        var client=applications.get(UUID.fromString(create("SERVER","PRIVATE_KEY_JWT").path("id").asText())).orElseThrow();
        var response=call(admin,"POST",path(client)+"/credentials",mapper.writeValueAsString(Map.of("name","Derived expiry", "type","PRIVATE_KEY", "algorithm","RS256", "certificateId",certificateId)));
        assertThat(response.statusCode()).isEqualTo(201);
        String credentialId=mapper.readTree(response.body()).path("id").asText();
        assertThat(sql.queryForObject("SELECT ExpireOn FROM Client.Credential WHERE RowGuid=?",LocalDateTime.class,credentialId))
                .isEqualTo(sql.queryForObject("SELECT ValidTo FROM Partner.Certificate WHERE RowGuid=?",LocalDateTime.class,certificateId));
    }
    @Test
    void validPublicCertificatePersistsWithoutPrivateMaterialInReadback() throws Exception {
        var response=upload(pem(false,false),"PUBLIC_KEY");
        assertThat(response.statusCode()).isEqualTo(201);
        String id=mapper.readTree(response.body()).path("id").asText();
        var read=call(admin,"GET","/api/v1/organizations/"+orgA.getRowGuid()+"/certificates/"+id,null);
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(read.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(read.body()).doesNotContain("PRIVATE KEY","keyStore","password");
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Partner.Certificate WHERE RowGuid=? AND DATALENGTH(KeyStore)>0",Integer.class,id)).isEqualTo(1);
    }
    @Test void certificateDeletionUsesStoredTypeAndCurrentPlatformAuthority() throws Exception {
        var created=upload(pem(false,true),"ORGANIZATION");
        assertThat(created.statusCode()).isEqualTo(201);
        String id=mapper.readTree(created.body()).path("id").asText();
        String path="/api/v1/organizations/"+orgA.getRowGuid()+"/certificates/"+id;
        assertThat(call(admin,"DELETE",path,null).statusCode()).isEqualTo(403);
        var foreign=token(user(orgB,group(orgB,"Foreign certificate deleter",List.of("idp-admin-cred-cert:delete")),"certificate-foreign"));
        assertThat(call(foreign,"DELETE",path,null).statusCode()).isEqualTo(403);
        assertThat(call(foreign,"DELETE",path.replace(id,UUID.randomUUID().toString()),null).statusCode()).isEqualTo(403);
        assertThat(sql.queryForObject("SELECT Status FROM Partner.Certificate WHERE RowGuid=?",Integer.class,id)).isEqualTo(1);
        assertThat(call(platform,"DELETE",path,null).statusCode()).isEqualTo(204);
        assertThat(sql.queryForObject("SELECT Status FROM Partner.Certificate WHERE RowGuid=?",Integer.class,id)).isZero();
        var publicKey=upload(pem(false,false),"PUBLIC_KEY");
        assertThat(publicKey.statusCode()).isEqualTo(201);
        String publicId=mapper.readTree(publicKey.body()).path("id").asText();
        assertThat(call(admin,"DELETE","/api/v1/organizations/"+orgA.getRowGuid()+"/certificates/"+publicId,null).statusCode()).isEqualTo(204);
        assertThat(sql.queryForObject("SELECT Status FROM Partner.Certificate WHERE RowGuid=?",Integer.class,publicId)).isZero();
    }

    @Test void organizationOneCertificateGrantWithoutSwitchCannotCreateSigningKey() throws Exception {
        var org=organizations.getById(1).orElseThrow();
        var actor=token(user(org,group(org,"Org certificate without switch",List.of("idp-admin-org-cert:create")),"certificate-no-switch"));
        int before=sql.queryForObject("SELECT COUNT(*) FROM Partner.Certificate",Integer.class);
        var response=upload(pem(false,true),"ORGANIZATION",actor,org.getRowGuid());
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Partner.Certificate",Integer.class)).isEqualTo(before);
    }

    @Test void certificateListAndDetailShareInjectedUtcBoundary() throws Exception {
        var created=upload(pem(false,false),"PUBLIC_KEY");
        assertThat(created.statusCode()).isEqualTo(201);
        String id=mapper.readTree(created.body()).path("id").asText();
        String collection="/api/v1/organizations/"+orgA.getRowGuid()+"/certificates";
        Instant end=sql.queryForObject("SELECT ValidTo FROM Partner.Certificate WHERE RowGuid=?",LocalDateTime.class,id).toInstant(ZoneOffset.UTC);
        for(Instant now:List.of(end.minusSeconds(3600),end,end.plusMillis(1))) {
            clock.fixed=now;
            var detail=certificateReadAtClock(collection+"/"+id);
            var list=certificateReadAtClock(collection);
            assertThat(detail.statusCode()).isEqualTo(200);
            assertThat(list.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(detail.body()).path("isExpired").asBoolean()).isEqualTo(now.isAfter(end));
            var entries=mapper.readTree(list.body());
            var match=java.util.stream.StreamSupport.stream(entries.spliterator(),false).filter(e->id.equals(e.path("id").asText())).findFirst().orElseThrow();
            assertThat(match.path("isExpired").asBoolean()).isEqualTo(now.isAfter(end));
        }
    }
    @Test void certificateWriteReloadsAuthorityAfterAuthentication() throws Exception {
        var org=organizations.getById(1).orElseThrow();
        int role=group(org,"Revocable certificate actor",List.of("idp-admin-org:switch","idp-admin-org-cert:create","idp-admin-org-cert:delete"));
        var actor=token(user(org,role,"certificate-revocable"));
        byte[] certificate=pem(false,true);
        var created=upload(certificate,"ORGANIZATION",actor,org.getRowGuid());
        assertThat(created.statusCode()).isEqualTo(201);
        String id=mapper.readTree(created.body()).path("id").asText();
        int before=sql.queryForObject("SELECT COUNT(*) FROM Partner.Certificate",Integer.class);
        for(String action:List.of("create","delete")) {
            var first=new java.util.concurrent.atomic.AtomicBoolean(true);
            Mockito.doAnswer(invocation->{
                if(first.compareAndSet(true,false)) {
                    // Controller entry is after real introspection/proof. Revoke before its lock/read.
                    assertThat(sql.update("UPDATE Partner.OrganizationGroupPermission SET Status=0 WHERE OrganizationGroupId=? AND PermissionKey=?",role,"idp-admin-org-cert:"+action)).isEqualTo(1);
                }
                return invocation.callRealMethod();
            }).when(mutationLock).acquire();
            try {
                var response=action.equals("create") ? upload(certificate,"ORGANIZATION",actor,org.getRowGuid())
                        : call(actor,"DELETE","/api/v1/organizations/"+org.getRowGuid()+"/certificates/"+id,null);
                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(sql.queryForObject("SELECT COUNT(*) FROM Partner.Certificate",Integer.class)).isEqualTo(before);
                assertThat(sql.queryForObject("SELECT Status FROM Partner.Certificate WHERE RowGuid=?",Integer.class,id)).isEqualTo(1);
            } finally {Mockito.reset(mutationLock);}
        }
    }

    HttpResponse<String> certificateReadAtClock(String path) throws Exception {
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(admin.key(),"HmacSHA256"));
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/services"+path))
                .header("Authorization","Bearer "+admin.value()).header("User-Agent","PolicyIntegration/1.0")
                .header("Accept-Language","en-US").header("x-request-datetime",clock.instant().toString())
                .header("x-signature",Base64.getEncoder().encodeToString(mac.doFinal(new byte[0]))).GET().build();
        return observed("human_tenant","GET",path,()->http.send(request,HttpResponse.BodyHandlers.ofString()));
    }

}
