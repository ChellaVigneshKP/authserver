package com.chellavignesh.authserver.recovery;

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
import com.chellavignesh.authserver.adminportal.organization.*;
import com.chellavignesh.authserver.adminportal.user.entity.User;
import com.chellavignesh.authserver.enums.entity.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
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
class CredentialMetadataIntegrationTest extends com.chellavignesh.authserver.support.ManagementHttpTestSupport {
    @org.springframework.beans.factory.annotation.Value("${server.base-path}") String issuer;
    static final OwnedDatabase database=new OwnedDatabase();
    @DynamicPropertySource static void isolate(DynamicPropertyRegistry registry) { database.register(registry); }
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean CredentialService credentials;
    @Autowired CredentialRepository credentialRepository;
    @Autowired SecretService secretService;
    @Autowired OrganizationCertificateService certificateService;
    @Autowired OrganizationRepository organizationRepository;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.chellavignesh.authserver.token.TokenService tokenService;
    Fixture adminToken,viewerToken;
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
    @AfterAll void cleanup() { cleanup(database); }

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
        assertThat(problem.path("requestId").asText()).isEqualTo("fixture-request-123");
        assertThat(problem.path("retryable").asBoolean()).isFalse();
        assertThat(denied.body()).doesNotContain("Exception",foreignApp.getRowGuid().toString());
    }
    void assertProblem(HttpResponse<String> response,int status,String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        var problem=mapper.readTree(response.body());
        assertThat(problem.properties().stream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("code","message","requestId","retryable");
        assertThat(problem.path("code").asText()).isEqualTo(code);
        assertThat(problem.path("message").asText().isBlank()).isFalse();
        assertThat(problem.path("requestId").asText()).isEqualTo("fixture-request-123");
        assertThat(problem.path("retryable").asBoolean()).isFalse();
        assertThat(response.body()).doesNotContain("Exception","stackTrace");
    }
    @Test void v2DeniedAccessUsesProblemEnvelope() throws Exception {
        var client=confidentialApplication();
        var stranger=user(orgB,group(orgB,"Envelope viewer",List.of("idp-admin-app:read")),"envelope-stranger");
        var bearer=token(stranger);
        assertThat(call(bearer,"GET",path(client),null).body()).isEqualTo("Access denied");
        assertProblem(call(bearer,"GET",path(client).replace("/v1/","/v2/"),null),403,"ACCESS_DENIED");
    }
    @Test void v2MalformedIdentifiersUseProblemEnvelope() throws Exception {
        var client=confidentialApplication();String route=path(client).replace("/v1/","/v2/");
        for(String guid:List.of(orgA.getRowGuid().toString(),client.getRowGuid().toString())) {
            var response=call(viewerToken,"GET",route.replace(guid,"malformed-guid-marker"),null);
            assertProblem(response,400,"INVALID_REQUEST");
            assertThat(response.body()).doesNotContain("malformed-guid-marker");
        }
    }
    @Test void v2MissingOrInvalidBearerUsesProblemEnvelope() throws Exception {
        var client=confidentialApplication();String route=path(client).replace("/v1/","/v2/");
        for(Fixture bearer:Arrays.asList(null,new Fixture("invalid-bearer-marker",new byte[32]))) {
            var legacy=call(bearer,"GET",path(client),null);
            assertThat(legacy.statusCode()).isEqualTo(401);
            assertThat(legacy.body()).isEmpty();
            var response=call(bearer,"GET",route,null);
            assertProblem(response,401,"UNAUTHORIZED");
            assertThat(response.headers().firstValue("WWW-Authenticate").orElse("")).startsWith("Bearer");
            assertThat(response.body()).doesNotContain("invalid-bearer-marker");
        }
    }

    void portalProblem(HttpResponse<String> response,int status,String requestId) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(response.headers().firstValue("x-request-id").orElse("")).isEqualTo(requestId);
        var body=mapper.readTree(response.body());
        assertThat(body.properties().stream().map(Map.Entry::getKey)).containsExactlyInAnyOrder("code","message","requestId","retryable");
        assertThat(body.path("requestId").asText()).isEqualTo(requestId);
        assertThat(body.path("retryable").asBoolean()).isEqualTo(status==503);
        assertThat(response.body()).doesNotContain("private-host","Exception","base64",adminToken.value());
        if(response.headers().firstValue("x-signature").filter(v->!v.isBlank()).isPresent()) {
            var mac=javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(adminToken.key(),"HmacSHA256"));
            assertThat(response.headers().firstValue("x-signature").orElseThrow()).isEqualTo(Base64.getEncoder().encodeToString(mac.doFinal(response.body().getBytes(StandardCharsets.UTF_8))));
        }
    }
    @Test void v2ProofFailuresShareSafePortalBoundary() throws Exception {
        for(String signature:Arrays.asList(null,"%",Base64.getEncoder().encodeToString(new byte[32]))) {
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/services"+path(app).replace("/v1/","/v2/")))
                    .header("Authorization","Bearer "+adminToken.value()).header("x-request-id","credential-proof-123")
                    .header("User-Agent","PolicyIntegration/1.0").header("Accept-Language","en-US")
                    .header("x-request-datetime",Instant.now().toString());
            if(signature!=null) request.header("x-signature",signature);
            var response=observed("human_tenant","GET",path(app).replace("/v1/","/v2/"),()->http.send(request.GET().build(),HttpResponse.BodyHandlers.ofString()));
            portalProblem(response,400,"credential-proof-123");
        }
    }
    @Test void v2DependencyOutageAndUnexpectedFailureAreSafe() throws Exception {
        var unavailable=new Fixture("v2-outage-"+UUID.randomUUID(),new byte[32]);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("private-host-secret"))
                .when(tokenService).getByValue(unavailable.value(),TokenTypeEnum.ACCESS_TOKEN);
        try {
            var response=call(unavailable,"GET",path(app).replace("/v1/","/v2/"),null);
            portalProblem(response,503,"fixture-request-123");
            assertThat(response.headers().firstValue("Retry-After").orElse("")).isEqualTo("5");
        } finally {org.mockito.Mockito.doCallRealMethod().when(tokenService).getByValue(unavailable.value(),TokenTypeEnum.ACCESS_TOKEN);}
        org.mockito.Mockito.doThrow(new IllegalStateException("private-host-secret"))
                .when(credentials).listMetadata(orgA.getRowGuid(),app.getRowGuid());
        try {
            portalProblem(call(adminToken,"GET",path(app).replace("/v1/","/v2/"),null),500,"fixture-request-123");
        } finally {org.mockito.Mockito.doCallRealMethod().when(credentials).listMetadata(orgA.getRowGuid(),app.getRowGuid());}
    }

    @Test void v2LocalDenialsPreserveRequestIdAndSignedBytes() throws Exception {
        portalProblem(call(null,"GET",path(app).replace("/v1/","/v2/"),null),401,"fixture-request-123");
        var denied=call(adminToken,"GET",path(app).replace("/v1/","/v2/").replace(orgA.getRowGuid().toString(),orgB.getRowGuid().toString()),null);
        assertThat(denied.headers().firstValue("x-signature").orElse("")).isNotBlank();
        portalProblem(denied,403,"fixture-request-123");
        portalProblem(call(adminToken,"GET",path(app).replace("/v1/","/v2/").replace(app.getRowGuid().toString(),"invalid"),null),400,"fixture-request-123");
    }

}
