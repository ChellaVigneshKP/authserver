package com.chellavignesh.authserver.adminportal.portal;

import com.chellavignesh.authserver.support.ManagementHttpTestSupport;
import com.chellavignesh.authserver.adminportal.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="AUTH_APP_IT", matches="true")
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "app.local.bootstrap.enabled=false", "toggles.signature.required=true", "toggles.account.sync.type=sync",
        "logging.level.root=WARN", "logback.access.enabled=false"})
class PortalContextIntegrationTest extends ManagementHttpTestSupport {
    static final OwnedDatabase database=new OwnedDatabase();
    @DynamicPropertySource static void isolate(DynamicPropertyRegistry registry) { database.register(registry); }
    @org.springframework.beans.factory.annotation.Autowired com.chellavignesh.authserver.security.ManagementAccessPolicy policy;
    @org.springframework.beans.factory.annotation.Autowired com.chellavignesh.authserver.adminportal.user.UserService userService;
    @org.springframework.beans.factory.annotation.Autowired com.chellavignesh.authserver.config.introspect.TokenIntrospector introspector;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.chellavignesh.authserver.token.TokenService tokenService;
    @org.springframework.beans.factory.annotation.Autowired com.chellavignesh.authserver.adminportal.credential.CredentialService credentials;
    User administrator,account,platform;
    Fixture adminToken,developerToken,viewerToken,accountToken,platformToken;
    static final String CONTEXT="/api/v1/portal/context";
    @BeforeAll void fixtures() throws Exception {
        orgA=organization("Context A"); orgB=organization("Context B");
        int adminGroup=group(orgA,"Context administrator",List.of("idp-admin-user:read","idp-admin-admin:read","idp-admin-user:update","idp-admin-admin:update","idp-admin-group:read"));
        int developerGroup=group(orgA,"Context developer",List.of("idp-admin-app:read","idp-admin-app:update"));
        int viewerGroup=group(orgA,"Context viewer",List.of("idp-admin-org:read"));
        administrator=user(orgA,adminGroup,"context-admin"); account=user(orgA,0,"context-account");
        var platformOrg=organizations.getById(1).orElseThrow();
        int platformGroup=group(platformOrg,"Context platform",List.of("idp-admin-org:switch","idp-admin-group:create","idp-admin-resource:create"));
        platform=user(platformOrg,platformGroup,"context-platform");
        app=application(orgA);
        redirects.createRedirectUris(orgA.getId(),app.getId(),List.of("http://localhost/callback"));
        adminToken=token(administrator);developerToken=token(user(orgA,developerGroup,"context-developer"));
        viewerToken=token(user(orgA,viewerGroup,"context-viewer"));accountToken=token(account);platformToken=token(platform);
    }
    @AfterAll void cleanup() { cleanup(database); }
    JsonNode context(Fixture token) throws Exception {
        var response=call(token,"GET",CONTEXT,null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(response.headers().firstValue("x-request-id").orElse("")).isEqualTo("fixture-request-123");
        assertSignature(token,response);
        return mapper.readTree(response.body());
    }
    List<String> values(JsonNode array) { List<String> result=new ArrayList<>();array.forEach(item->result.add(item.asText()));return result; }
    @Test void contextUsesHumanPermissions() throws Exception {
        var body=context(adminToken);
        assertThat(body.properties().stream().map(Map.Entry::getKey)).containsExactlyInAnyOrder("subject","organizations","permissions","capabilities");
        var subject=body.path("subject");
        assertThat(subject.properties().stream().map(Map.Entry::getKey)).containsExactlyInAnyOrder("userId","loginId","username","organizationId");
        assertThat(subject.path("userId").asText()).isEqualTo(administrator.getRowGuid().toString());
        assertThat(subject.path("loginId").asText()).isEqualTo(administrator.getLoginId().toString());
        assertThat(subject.path("username").asText()).isEqualTo("context-admin");
        assertThat(subject.path("organizationId").asText()).isEqualTo(orgA.getRowGuid().toString());
        assertThat(values(body.path("permissions"))).containsExactlyInAnyOrder("idp-admin-user:read","idp-admin-admin:read","idp-admin-user:update","idp-admin-admin:update","idp-admin-group:read");
        assertThat(body.path("capabilities").path("userWrite").asBoolean()).isTrue();
        assertThat(body.path("capabilities").path("roleWrite").asBoolean()).isFalse();
        assertThat(body.path("capabilities").path("resourceWrite").asBoolean()).isFalse();
        assertThat(context(platformToken).path("capabilities").path("roleWrite").asBoolean()).isTrue();
        assertThat(context(platformToken).path("capabilities").path("resourceWrite").asBoolean()).isTrue();
        assertThat(values(context(developerToken).path("permissions"))).containsExactlyInAnyOrder("idp-admin-app:read","idp-admin-app:update");
        assertThat(values(context(viewerToken).path("permissions"))).containsExactly("idp-admin-org:read");
    }
    @Test void contextListsOnlyAccessibleOrganizations() throws Exception {
        for(Fixture token:List.of(adminToken,developerToken,viewerToken,accountToken)) {
            var accessible=context(token).path("organizations"); assertThat(accessible).hasSize(1);
            assertThat(accessible.get(0).path("id").asText()).isEqualTo(orgA.getRowGuid().toString());
            assertThat(accessible.get(0).path("name").asText()).isEqualTo("Context A");
        }
        var all=context(platformToken).path("organizations");
        assertThat(all.findValuesAsText("id")).contains(orgA.getRowGuid().toString(),orgB.getRowGuid().toString());
        assertThat(context(platformToken).path("subject").path("userId").asText()).isEqualTo(platform.getRowGuid().toString());
    }
    @Test void accountUserHasNoAdminCapabilities() throws Exception {
        var body=context(accountToken);assertThat(body.path("permissions")).isEmpty();
        var capabilities=body.path("capabilities");
        assertThat(capabilities.properties().stream().map(Map.Entry::getKey)).containsExactlyInAnyOrder("roleWrite","userWrite","resourceWrite","providerMode");
        for(String key:List.of("roleWrite","userWrite","resourceWrite")) assertThat(capabilities.path(key).asBoolean()).isFalse();
        assertThat(capabilities.path("providerMode").asText()).isEqualTo("unconfigured");
    }
    @Test void permissionRemovalVisibleOnNextRequest() throws Exception {
        int role=group(orgA,"Context revocable",List.of("idp-admin-user:update"));
        var user=user(orgA,role,"context-revocable");var bearer=token(user);
        assertThat(context(bearer).path("capabilities").path("userWrite").asBoolean()).isTrue();
        sql.update("UPDATE Partner.OrganizationGroupPermission SET Status=0 WHERE OrganizationGroupId=?",role);
        assertThat(context(bearer).path("permissions")).isEmpty();
        assertThat(context(bearer).path("capabilities").path("userWrite").asBoolean()).isFalse();
        int replacement=group(orgA,"Context reassigned",List.of("idp-admin-app:read"));
        sql.update("UPDATE Person.ProfileGroup SET OrganizationGroupId=? WHERE ProfileId=?",replacement,user.getId());
        assertThat(values(context(bearer).path("permissions"))).containsExactly("idp-admin-app:read");
    }
    @Test void contextRejectsAmbiguousMembershipsAndRecoversAfterCorrection() throws Exception {
        var member=user(orgA,0,"context-multiple-memberships");var bearer=token(member);
        assertThat(context(bearer).path("organizations")).hasSize(1);
        sql.update("INSERT Person.ProfileOrganization(ProfileId,OrganizationId) VALUES(?,?)",member.getId(),orgB.getId());
        // B5's singular actor scope must never select an arbitrary imported active membership.
        var ambiguous=call(bearer,"GET",CONTEXT,null);
        assertThat(ambiguous.statusCode()).isEqualTo(401);assertProblem(ambiguous,false);
        assertThat(ambiguous.body()).doesNotContain("subject","organizations");
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.ProfileOrganization WHERE ProfileId=? AND Status=1",Integer.class,member.getId())).isEqualTo(2);
        sql.update("UPDATE Person.ProfileOrganization SET Status=0 WHERE ProfileId=? AND OrganizationId=?",member.getId(),orgB.getId());
        assertThat(context(bearer).path("organizations").findValuesAsText("id")).containsExactly(orgA.getRowGuid().toString());
    }
    @Test void removedOwnMembershipDeniesContext() throws Exception {
        var member=user(orgA,0,"context-removed-membership");var bearer=token(member);
        assertThat(context(bearer).path("organizations")).hasSize(1);
        sql.update("UPDATE Person.ProfileOrganization SET Status=0 WHERE ProfileId=?",member.getId());
        var response=call(bearer,"GET",CONTEXT,null);
        // B5 invalidates the bearer during live introspection, before any raw permission evaluator.
        assertThat(response.statusCode()).isEqualTo(401);assertProblem(response,false);
        assertThat(response.body()).doesNotContain("subject","organizations");
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.ProfileOrganization WHERE ProfileId=? AND Status=1",Integer.class,member.getId())).isZero();
        assertThat(sql.queryForObject("SELECT Status FROM Person.Profile WHERE ProfileId=?",Integer.class,member.getId())).isEqualTo(1);
        // Deliberate operator correction restores access with the retained token; the read repaired nothing.
        sql.update("UPDATE Person.ProfileOrganization SET Status=1 WHERE ProfileId=?",member.getId());
        assertThat(context(bearer).path("subject").path("userId").asText()).isEqualTo(member.getRowGuid().toString());
    }
    @Test void removedPlatformMembershipDeniesContext() throws Exception {
        var platformOrg=organizations.getById(1).orElseThrow();
        int role=group(platformOrg,"Revocable platform",List.of("idp-admin-org:switch"));
        var member=user(platformOrg,role,"context-removed-platform");var bearer=token(member);
        assertThat(context(bearer).path("organizations").findValuesAsText("id"))
                .contains(orgA.getRowGuid().toString(),orgB.getRowGuid().toString());
        sql.update("UPDATE Person.ProfileOrganization SET Status=0 WHERE ProfileId=?",member.getId());
        var response=call(bearer,"GET",CONTEXT,null);
        // B5 invalidates the bearer during live introspection, before any raw permission evaluator.
        assertThat(response.statusCode()).isEqualTo(401);assertProblem(response,false);
        assertThat(response.body()).doesNotContain("subject","organizations");
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.ProfileOrganization WHERE ProfileId=? AND Status=1",Integer.class,member.getId())).isZero();
        assertThat(sql.queryForObject("SELECT Status FROM Person.Profile WHERE ProfileId=?",Integer.class,member.getId())).isEqualTo(1);
        // Deliberate operator correction restores access with the retained token; the read repaired nothing.
        sql.update("UPDATE Person.ProfileOrganization SET Status=1 WHERE ProfileId=?",member.getId());
        assertThat(context(bearer).path("subject").path("userId").asText()).isEqualTo(member.getRowGuid().toString());
        assertThat(response.body()).doesNotContain("subject","organizations",orgB.getRowGuid().toString());
    }
    @Test void providerModeDescribesConfigurationOnly() throws Exception {
        var principal=introspector.introspect(adminToken.value());
        var token=new org.springframework.security.oauth2.core.OAuth2AccessToken(
                org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER,adminToken.value(),
                java.time.Instant.now(),java.time.Instant.now().plusSeconds(600));
        var authentication=new org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication(principal,token,List.of());
        for(String mode:List.of("unconfigured","simulated","external")) {
            var result=new PortalContextService(policy,userService,organizations,mode).getContext(authentication);
            assertThat(result.capabilities().providerMode()).isEqualTo(mode);
            assertThat(mapper.writeValueAsString(result)).doesNotContain("certified","connected","healthy");
        }
        assertThatIllegalArgumentException().isThrownBy(()->new PortalContextService(policy,userService,organizations,"certified"));
    }
    @Test void introspectionOutageReturnsRetryable503() throws Exception {
        var unavailable=new Fixture("outage-"+UUID.randomUUID(),new byte[32]);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("private-host-and-credentials"))
                .when(tokenService).getByValue(unavailable.value(),com.chellavignesh.authserver.enums.entity.TokenTypeEnum.ACCESS_TOKEN);
        try {
            var response=call(unavailable,"GET",CONTEXT,null);
            assertThat(response.statusCode()).isEqualTo(503);assertProblem(response,true);
            assertThat(response.headers().firstValue("Retry-After").orElse("")).isEqualTo("5");
            assertThat(response.body()).doesNotContain("private-host","credentials","Exception",unavailable.value());
        } finally {
            org.mockito.Mockito.doCallRealMethod().when(tokenService).getByValue(unavailable.value(),com.chellavignesh.authserver.enums.entity.TokenTypeEnum.ACCESS_TOKEN);
        }
    }
    @Test void contextContainsNoSecrets() throws Exception {
        var body=context(adminToken);
        assertThat(body.toString()).doesNotContain(adminToken.value(),Base64.getEncoder().encodeToString(adminToken.key()),"password","signing_key","metadata","secret","certified");
    }
    @Test void userDtoIncludesAuthorizedOrgAndGroup() throws Exception {
        var response=call(adminToken,"GET","/api/v1/users/"+administrator.getRowGuid(),null);
        assertThat(response.statusCode()).isEqualTo(200);var body=mapper.readTree(response.body());
        assertThat(body.path("id").asText()).isEqualTo(administrator.getRowGuid().toString());
        assertThat(body.path("userName").asText()).isEqualTo("context-admin");
        assertThat(body.path("organizationId").asText()).isEqualTo(orgA.getRowGuid().toString());
        assertThat(UUID.fromString(body.path("groupId").asText())).isNotNull();
        assertThat(body.path("syncMode").asText()).isEqualTo("local");
        var local=mapper.readTree(call(adminToken,"GET","/api/v1/users/"+account.getRowGuid(),null).body());
        assertThat(local.has("groupId")).isTrue();assertThat(local.path("groupId").isNull()).isTrue();
        sql.update("UPDATE Person.Profile SET SyncFlag=1 WHERE ProfileId=?",account.getId());
        var synced=mapper.readTree(call(adminToken,"GET","/api/v1/users/"+account.getRowGuid(),null).body());
        assertThat(synced.path("syncMode").asText()).isEqualTo("sync");
        var actual=users.getByGuid(account.getRowGuid()).orElseThrow();
        assertThat(com.chellavignesh.authserver.adminportal.user.dto.UserResponseDto.fromUser(actual,
                com.chellavignesh.authserver.adminportal.toggle.AccountSyncType.ASYNC).syncMode()).isEqualTo("async");
        assertThat(com.chellavignesh.authserver.adminportal.user.dto.UserResponseDto.fromUser(actual,
                com.chellavignesh.authserver.adminportal.toggle.AccountSyncType.NONE).syncMode()).isEqualTo("local");
        var list=mapper.readTree(call(adminToken,"GET","/api/v1/users",null).body()).path("users");
        assertThat(list).isNotEmpty();for(var item:list) assertThat(item.path("organizationId").asText()).isEqualTo(orgA.getRowGuid().toString());
    }
    @Test void contextRejectsClientCredentialActor() throws Exception {
        var clientDto=new com.chellavignesh.authserver.adminportal.application.dto.CreateApplicationDto();
        clientDto.setName("Context machine");clientDto.setDescription("Owned fixture");
        clientDto.setType(com.chellavignesh.authserver.enums.entity.ApplicationTypeEnum.SERVER);
        clientDto.setAuthMethod(com.chellavignesh.authserver.enums.entity.AuthFlowEnum.CLIENT_SECRET_JWT);
        clientDto.setUsernameType(com.chellavignesh.authserver.enums.entity.UsernameTypeEnum.USERNAME);clientDto.setAllowForgotUsername(false);
        var settings=new com.chellavignesh.authserver.adminportal.application.dto.UpdateTokenSettingsDto();
        settings.setAuthCodeTimeToLive(120);settings.setAccessTokenTimeToLive(600);settings.setRefreshTokenTimeToLive(3600);
        settings.setDeviceCodeTimeToLive(120);settings.setMaxRequestTransitTime(30);settings.setReuseRefreshTokens(false);
        var client=applications.create(orgA.getId(),clientDto,settings,new com.chellavignesh.authserver.adminportal.forgotusername.ForgotUsernameSetting(List.of()),
                com.chellavignesh.authserver.enums.entity.AccessTokenFormatEnum.REFERENCE);
        var secret=new com.chellavignesh.authserver.adminportal.credential.dto.CreateCredentialRequestDto();
        secret.setOrgId(orgA.getId());secret.setAppId(client.getId());secret.setName("Context machine credential");secret.setDescription("Owned fixture");
        secret.setAlgorithmEnum(com.chellavignesh.authserver.enums.entity.AlgorithmEnum.HS256);
        secret.setAuthFlow(com.chellavignesh.authserver.enums.entity.AuthFlowEnum.CLIENT_SECRET_JWT);
        secret.setCredentialStatus(com.chellavignesh.authserver.adminportal.credential.CredentialStatus.Active);
        secret.setExpireOn(Date.from(java.time.Instant.now().plusSeconds(86400)));
        credentials.createSharedSecretCredential(secret);
        var machine=token(client,client.getClientId(),"confidential-client");
        var response=call(machine,"GET",CONTEXT,null);assertThat(response.statusCode()).isEqualTo(403);assertProblem(response,false);assertSignature(machine,response);
        assertThat(response.body()).doesNotContain("subject","permissions");
    }
    void assertSignature(Fixture token,HttpResponse<String> response) throws Exception {
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(token.key(),"HmacSHA256"));
        assertThat(response.headers().firstValue("x-signature").orElse(""))
                .isEqualTo(Base64.getEncoder().encodeToString(mac.doFinal(response.body().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
    void assertProblem(HttpResponse<String> response,boolean retryable) throws Exception {
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        var body=mapper.readTree(response.body());
        assertThat(body.properties().stream().map(Map.Entry::getKey)).containsExactlyInAnyOrder("code","message","requestId","retryable");
        assertThat(body.path("requestId").asText()).isNotBlank();assertThat(body.path("retryable").asBoolean()).isEqualTo(retryable);
        assertThat(body.path("code").asText()).isNotBlank();assertThat(body.path("message").asText()).isNotBlank();
    }
    @Test void invalidBearerHasSafeUnauthorizedContract() throws Exception {
        for(Fixture token:Arrays.asList(null,new Fixture("invalid-"+UUID.randomUUID(),new byte[32]))) {
            var response=call(token,"GET",CONTEXT,null);assertThat(response.statusCode()).isEqualTo(401);assertProblem(response,false);
            assertThat(response.body()).doesNotContain("subject","permissions","invalid-");
        }
    }
    @Test void rejectedProofHasSafeContractAndValidatedRequestId() throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/services"+CONTEXT))
                .header("Authorization","Bearer "+adminToken.value()).header("x-request-id","bad request id")
                .header("User-Agent","PolicyIntegration/1.0").header("Accept-Language","en-US")
                .header("x-request-datetime",java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()).GET().build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(400);assertProblem(response,false);
        assertThat(UUID.fromString(mapper.readTree(response.body()).path("requestId").asText())).isNotNull();
    }    @Test void malformedSignatureEncodingHasSafeProofRejection() throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/services"+CONTEXT))
                .header("Authorization","Bearer "+adminToken.value()).header("x-signature","%")
                .header("x-request-id","malformed-proof-123")
                .header("User-Agent","PolicyIntegration/1.0").header("Accept-Language","en-US")
                .header("x-request-datetime",java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()).GET().build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);assertProblem(response,false);
        assertThat(mapper.readTree(response.body()).path("code").asText()).isEqualTo("INVALID_REQUEST");
        assertThat(mapper.readTree(response.body()).path("requestId").asText()).isEqualTo("malformed-proof-123");
        assertThat(response.body()).doesNotContain("subject","IllegalArgumentException","base64",adminToken.value());
    }

}
