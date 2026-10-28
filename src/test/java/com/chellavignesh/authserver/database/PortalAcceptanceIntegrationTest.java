package com.chellavignesh.authserver.database;

import com.chellavignesh.authserver.support.ManagementHttpTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Final route-denial matrix, plus persisted local mutations absent from the earlier campaigns. */
@EnabledIfEnvironmentVariable(named="AUTH_APP_IT", matches="true")
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "app.local.bootstrap.enabled=false", "toggles.signature.required=true",
        "logging.level.root=WARN", "logback.access.enabled=false"})
class PortalAcceptanceIntegrationTest extends ManagementHttpTestSupport {
    static final OwnedDatabase database = new OwnedDatabase();
    @DynamicPropertySource static void isolate(DynamicPropertyRegistry registry) { database.register(registry); }
    @org.springframework.beans.factory.annotation.Autowired com.chellavignesh.authserver.adminportal.credential.CredentialService credentials;
    @org.springframework.beans.factory.annotation.Autowired com.chellavignesh.authserver.adminportal.resource.ResourceRepository resources;
    Fixture platform;
    String organization, client;
    @BeforeAll void fixtures() throws Exception {
        orgA=organization("Acceptance A"); orgB=organization("Acceptance B"); app=application(orgA);
        redirects.createRedirectUris(orgA.getId(),app.getId(),List.of("http://localhost/callback"));
        platform=token(user(organizations.getById(1).orElseThrow(),1,"acceptance-platform"));
        organization="/api/v1/organizations/"+orgA.getRowGuid();
        client=organization+"/applications/"+app.getRowGuid();
    }
    @AfterAll void cleanup() { cleanup(database); }

    @Test void everyManagementMutationRejectsMissingBearerWithoutSqlChanges() throws Exception {
        var target = user(orgA,0,"acceptance-denial");
        String user="/api/v1/users/"+target.getRowGuid(), id=UUID.randomUUID().toString();
        var routes = new LinkedHashMap<String,String>();
        routes.put("POST /api/v1/organizations", "{}");
        routes.put("PUT "+organization, "{}");
        routes.put("PUT "+organization+"/primary-contact", "{}");
        routes.put("PUT "+organization+"/secondary-contact", "{}");
        routes.put("POST "+organization+"/applications", "{}");
        routes.put("PUT "+client, "{}"); routes.put("DELETE "+client, null);
        routes.put("PUT "+client+"/urls", "{}"); routes.put("PUT "+client+"/settings", "{}");
        routes.put("POST "+client+"/endpoints", "{}"); routes.put("DELETE "+client+"/endpoints/"+id, null);
        routes.put("POST "+client+"/credentials", "{}"); routes.put("PUT "+client+"/credentials/"+id, "{}");
        routes.put("DELETE "+client+"/credentials/"+id, null);
        routes.put("POST "+organization+"/certificates", "{}"); routes.put("DELETE "+organization+"/certificates/"+id, null);
        routes.put("POST /api/v1/endpoints", "{}"); routes.put("PUT /api/v1/endpoints/"+id, "{}");
        routes.put("POST /api/v1/users", "{}"); routes.put("POST /api/v1/users/profile", "{}");
        for (String suffix:List.of("profile","email","password","username","security-settings","metadata","status"))
            routes.put("PUT "+user+"/"+suffix, "{}");
        routes.put("DELETE "+user, null);
        routes.put("POST "+organization+"/groups", "{}"); routes.put("PUT "+organization+"/groups/"+id, "{}");
        routes.put("DELETE "+organization+"/groups/"+id, null);
        routes.put("PUT "+organization+"/users/"+target.getRowGuid()+"/group", "{}");
        // Exact public structural readback is performed around each real request by the observer.
        // Independent SQL count/status checks protect the normal non-observation test gate too.
        int profiles=sql.queryForObject("SELECT COUNT(*) FROM Person.Profile",Integer.class);
        int applications=sql.queryForObject("SELECT COUNT(*) FROM Client.Application",Integer.class);
        for (var entry:routes.entrySet()) {
            String[] route=entry.getKey().split(" ",2);
            var before = com.chellavignesh.authserver.support.ManagementObservations.snapshot(sql);
            assertThat(call(null,route[0],route[1],entry.getValue()).statusCode()).as(entry.getKey()).isEqualTo(401);
            assertThat(com.chellavignesh.authserver.support.ManagementObservations.snapshot(sql)).isEqualTo(before);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.Profile",Integer.class)).isEqualTo(profiles);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM Client.Application",Integer.class)).isEqualTo(applications);
            assertThat(sql.queryForObject("SELECT Status FROM Partner.Organization WHERE OrganizationId=?",Integer.class,orgA.getId())).isEqualTo(1);
        }
    }

    @Test void localUserSecurityAndDeletionPersistWithPermissionDenials() throws Exception {
        var target = user(orgA, 0, "acceptance-local-target");
        sql.update("UPDATE Person.Profile SET TwoFactorEnabled=0 WHERE ProfileId=?", target.getId());
        assertThat(sql.queryForObject("SELECT TwoFactorEnabled FROM Person.Profile WHERE ProfileId=?", Boolean.class, target.getId())).isFalse();
        var reader = token(user(orgA, 0, "acceptance-no-management-grants"));
        String path = "/api/v1/users/"+target.getRowGuid();
        for (String method : List.of("PUT", "DELETE")) {
            var before = com.chellavignesh.authserver.support.ManagementObservations.snapshot(sql);
            assertThat(call(reader, method, path+(method.equals("PUT")?"/security-settings":""),
                    method.equals("PUT")?"{\"twoFactorEnabled\":true}":null).statusCode()).isEqualTo(403);
            assertThat(com.chellavignesh.authserver.support.ManagementObservations.snapshot(sql)).isEqualTo(before);
        }
        var beforeMutation = com.chellavignesh.authserver.support.ManagementObservations.snapshot(sql);
        assertThat(call(platform, "PUT", path+"/security-settings", "{\"twoFactorEnabled\":true}").statusCode()).isEqualTo(200);
        assertThat(sql.queryForObject("SELECT TwoFactorEnabled FROM Person.Profile WHERE ProfileId=?", Boolean.class, target.getId())).isTrue();
        assertThat(com.chellavignesh.authserver.support.ManagementObservations.snapshot(sql).get("Person.Profile"))
                .isNotEqualTo(beforeMutation.get("Person.Profile"));
        assertThat(call(platform, "DELETE", path, null).statusCode()).isEqualTo(204);
        assertThat(sql.queryForObject("SELECT Status FROM Person.Profile WHERE ProfileId=?", Integer.class, target.getId())).isZero();
    }

    @Test void organizationCreationContactsAndSettingsPersist() throws Exception {
        var created=call(platform,"POST","/api/v1/organizations","{\"name\":\"Acceptance created organization\",\"description\":\"Owned\"}");
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Partner.Organization WHERE Name='Acceptance created organization'",Integer.class)).isEqualTo(1);
        for(String kind:List.of("primary", "secondary")) {
            assertThat(call(platform,"PUT",organization+"/"+kind+"-contact","{\"name\":\"Acceptance contact\",\"email\":\"contact@example.test\",\"phoneNumber\":\"+19999999999\"}").statusCode()).isEqualTo(200);
            String column=kind.equals("primary")?"PrimaryContactName":"SecondaryContactName";
            assertThat(sql.queryForObject("SELECT "+column+" FROM Partner.Organization WHERE OrganizationId=?",String.class,orgA.getId())).isEqualTo("Acceptance contact");
        }
        assertThat(call(platform,"PUT",client+"/settings","{\"authCodeTimeToLive\":180,\"accessTokenTimeToLive\":650,\"refreshTokenTimeToLive\":3600,\"deviceCodeTimeToLive\":120,\"maxRequestTransitTime\":30,\"reuseRefreshTokens\":false}").statusCode()).isEqualTo(200);
        assertThat(sql.queryForObject("SELECT AccessTokenTimeToLive FROM Client.TokenSetting WHERE ApplicationId=?",Integer.class,app.getId())).isEqualTo(650);
    }

    @Test void resourceCatalogAndAssignmentPersistAndDelete() throws Exception {
        String body="{\"name\":\"Acceptance resource\",\"description\":\"Owned fixture\",\"uri\":\"https://example.test/acceptance\",\"allowedMethod\":\"GET\",\"urn\":\"urn:acceptance:resource\"}";
        var created=call(platform,"POST","/api/v1/endpoints",body); assertThat(created.statusCode()).isEqualTo(201);
        String id=mapper.readTree(created.body()).path("id").asText();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM dbo.ResourceLibrary WHERE RowGuid=?",Integer.class,id)).isEqualTo(1);
        assertThat(call(platform,"PUT","/api/v1/endpoints/"+id,body.replace("Owned fixture","Updated fixture")).statusCode()).isEqualTo(200);
        assertThat(sql.queryForObject("SELECT Description FROM dbo.ResourceLibrary WHERE RowGuid=?",String.class,id)).isEqualTo("Updated fixture");
        assertThat(call(platform,"POST",client+"/endpoints","{\"resourceId\":\""+id+"\"}").statusCode()).isEqualTo(201);
        String assigned=sql.queryForObject("SELECT CONVERT(VARCHAR(36),RowGuid) FROM Resource.Resource WHERE ApplicationId=?",String.class,app.getId());
        assertThat(call(platform,"DELETE",client+"/endpoints/"+id,null).statusCode()).isEqualTo(204);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Resource.Resource WHERE RowGuid=?",Integer.class,assigned)).isZero();
    }
    @Test void inactiveTenantHistoryDoesNotLeakRowsOrTotals() throws Exception {
        var moved = user(orgB, 0, "history-moved");
        sql.update("INSERT Person.ProfileOrganization(ProfileId,OrganizationId,Status) VALUES(?,?,0)", moved.getId(), orgA.getId());
        var ordinary = token(user(orgA, group(orgA,"History ordinary reader",List.of("idp-admin-user:read")), "history-reader"));
        for (Fixture actor : List.of(ordinary, platform)) {
            var response = call(actor,"GET","/api/v1/users?organizationId="+orgA.getRowGuid()+"&search=history-moved",null);
            assertThat(response.statusCode()).isEqualTo(200);
            var body = mapper.readTree(response.body());
            assertThat(body.path("results").asInt()).isZero();
            assertThat(body.path("users")).isEmpty();
            assertThat(response.body()).doesNotContain("history-moved");
        }
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.ProfileOrganization WHERE ProfileId=?",Integer.class,moved.getId())).isEqualTo(2);
    }

    @Test void administratorWithInactiveForeignRoleHistoryIsClassifiedOnce() throws Exception {
        var target = user(orgA,group(orgA,"Historical administrator",List.of("idp-admin-app:read")),"history-admin");
        int foreign = group(orgB,"Historical foreign group",List.of("idp-admin-app:read"));
        sql.update("UPDATE Partner.OrganizationGroup SET Status=0 WHERE OrganizationGroupId=?",foreign);
        sql.update("INSERT Person.ProfileGroup(ProfileId,OrganizationGroupId) VALUES(?,?)",target.getId(),foreign);
        var ordinary = token(user(orgA,group(orgA,"Ordinary-only reader",List.of("idp-admin-user:read")),"history-ordinary"));
        var administrators = token(user(orgA,group(orgA,"Admin-only reader",List.of("idp-admin-admin:read")),"history-admin-reader"));
        for (Fixture actor : List.of(ordinary,administrators,platform)) {
            var response=call(actor,"GET","/api/v1/users?organizationId="+orgA.getRowGuid()+"&search=history-admin@example.test",null);
            assertThat(response.statusCode()).isEqualTo(200);
            var body=mapper.readTree(response.body());
            int expected=actor==ordinary?0:1;
            assertThat(body.path("results").asInt()).isEqualTo(expected);
            assertThat(body.path("users")).hasSize(expected);
        }
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.ProfileGroup WHERE ProfileId=?",Integer.class,target.getId())).isEqualTo(2);
    }

    @Test void ambiguousActiveMembershipsAreOmittedWithoutRepair() throws Exception {
        var target=user(orgA,0,"history-ambiguous");
        sql.update("INSERT Person.ProfileOrganization(ProfileId,OrganizationId) VALUES(?,?)",target.getId(),orgB.getId());
        var response=call(platform,"GET","/api/v1/users?organizationId="+orgA.getRowGuid()+"&search=history-ambiguous",null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).path("results").asInt()).isZero();
        assertThat(mapper.readTree(response.body()).path("users")).isEmpty();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.ProfileOrganization WHERE ProfileId=? AND Status=1",Integer.class,target.getId())).isEqualTo(2);
    }

    String localBrand() {
        // The IDP source and its CMS template are installed into this owned UUID database.
        assertThat(sql.update("UPDATE dbo.ExternalSource SET SyncFlag=0 WHERE SourceCode='idp'")).isEqualTo(1);
        return "idp";
    }
    String createBody(UUID organization,UUID group,String name,String brand) throws Exception {
        var body=new LinkedHashMap<String,Object>();
        body.put("orgGuid",organization);body.put("groupGuid",group);body.put("firstName","Owned");body.put("lastName","Fixture");
        body.put("username",name);body.put("email",name+"@example.test");body.put("phoneNumber","+19999999999");
        body.put("password",Base64.getEncoder().encodeToString("Zq8!vR6@kN4$".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        body.put("branding",brand);body.put("syncFlag",false);
        return mapper.writeValueAsString(body);
    }
    Fixture machine(com.chellavignesh.authserver.adminportal.organization.entity.Organization organization) throws Exception {
        var dto=new com.chellavignesh.authserver.adminportal.application.dto.CreateApplicationDto();
        dto.setName("Owned provisioning machine");dto.setDescription("Fixture");
        dto.setType(com.chellavignesh.authserver.enums.entity.ApplicationTypeEnum.SERVER);
        dto.setAuthMethod(com.chellavignesh.authserver.enums.entity.AuthFlowEnum.CLIENT_SECRET_JWT);
        dto.setUsernameType(com.chellavignesh.authserver.enums.entity.UsernameTypeEnum.USERNAME);dto.setAllowForgotUsername(false);
        var settings=new com.chellavignesh.authserver.adminportal.application.dto.UpdateTokenSettingsDto();
        settings.setAuthCodeTimeToLive(120);settings.setAccessTokenTimeToLive(600);settings.setRefreshTokenTimeToLive(3600);
        settings.setDeviceCodeTimeToLive(120);settings.setMaxRequestTransitTime(30);settings.setReuseRefreshTokens(false);
        var client=applications.create(organization.getId(),dto,settings,new com.chellavignesh.authserver.adminportal.forgotusername.ForgotUsernameSetting(List.of()),com.chellavignesh.authserver.enums.entity.AccessTokenFormatEnum.REFERENCE);
        var secret=new com.chellavignesh.authserver.adminportal.credential.dto.CreateCredentialRequestDto();
        secret.setOrgId(organization.getId());secret.setAppId(client.getId());secret.setName("Owned machine credential");secret.setDescription("Fixture");
        secret.setAlgorithmEnum(com.chellavignesh.authserver.enums.entity.AlgorithmEnum.HS256);
        secret.setAuthFlow(com.chellavignesh.authserver.enums.entity.AuthFlowEnum.CLIENT_SECRET_JWT);
        secret.setCredentialStatus(com.chellavignesh.authserver.adminportal.credential.CredentialStatus.Active);
        secret.setExpireOn(Date.from(java.time.Instant.now().plusSeconds(86400)));credentials.createSharedSecretCredential(secret);
        var resource=new com.chellavignesh.authserver.adminportal.resource.dto.ResourceLibraryDto();
        resource.setName("Owned machine POST "+UUID.randomUUID());resource.setDescription("Fixture");resource.setUri("http://user");resource.setAllowedMethod("POST");resource.setUrn("urn:owned:post:"+UUID.randomUUID());
        var grant=resources.create(resource);applications.assignResource(organization.getId(),client.getId(),grant.getId());
        return token(client,client.getClientId(),"confidential-client");
    }
    @Test void machineCannotProvisionReservedHumanAuthorityButOrdinaryCreationWorks() throws Exception {
        var org=organizations.getById(1).orElseThrow();
        var actor=machine(org);String brand=localBrand();
        for(int template:new int[]{0,-1,2,3,1}) {
            int role=group(org,"Machine reserved "+template,List.of("idp-admin-org:switch"));
            if(template==-1) sql.update("UPDATE Partner.OrganizationGroup SET IsBuiltin=1 WHERE OrganizationGroupId=?",role);
            if(template>0) sql.update("UPDATE Partner.OrganizationGroup SET IsBuiltin=1,GroupTemplateId=? WHERE OrganizationGroupId=?",template,role);
            String group=sql.queryForObject("SELECT CONVERT(VARCHAR(36),RowGuid) FROM Partner.OrganizationGroup WHERE OrganizationGroupId=?",String.class,role);
            var before=com.chellavignesh.authserver.support.ManagementObservations.snapshot(sql);
            int profiles=sql.queryForObject("SELECT COUNT(*) FROM Person.Profile",Integer.class);
            var response=call(actor,"POST","/api/v1/users",createBody(org.getRowGuid(),UUID.fromString(group),"reserved-machine-"+template,brand));
            assertThat(response.statusCode()).as("template "+template).isEqualTo(403);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.Profile",Integer.class)).isEqualTo(profiles);
            assertThat(com.chellavignesh.authserver.support.ManagementObservations.snapshot(sql)).isEqualTo(before);
        }
        var response=call(actor,"POST","/api/v1/users",createBody(org.getRowGuid(),null,"ordinary-machine-created",brand));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        String id=mapper.readTree(response.body()).path("id").asText();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.Profile p JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId WHERE p.RowGuid=? AND po.OrganizationId=? AND po.Status=1",Integer.class,id,org.getId())).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.ProfileGroup pg JOIN Person.Profile p ON p.ProfileId=pg.ProfileId WHERE p.RowGuid=?",Integer.class,id)).isZero();
    }

    @Test void supportedLocalUserCreationAndUpdatesPersistWithPermissionDenials() throws Exception {
        String brand=localBrand();
        var denied=token(user(orgA,0,"local-denied-actor"));
        String payload=createBody(orgA.getRowGuid(),null,"local-http-created",brand);
        var before=com.chellavignesh.authserver.support.ManagementObservations.snapshot(sql);
        assertThat(call(denied,"POST","/api/v1/users",payload).statusCode()).isEqualTo(403);
        assertThat(com.chellavignesh.authserver.support.ManagementObservations.snapshot(sql)).isEqualTo(before);
        var response=call(platform,"POST","/api/v1/users",payload);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        String id=mapper.readTree(response.body()).path("id").asText();
        String path="/api/v1/users/"+id;
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId WHERE p.RowGuid=? AND c.UserName='local-http-created' AND p.SyncFlag=0 AND c.SyncFlag=0 AND po.OrganizationId=? AND po.Status=1",Integer.class,id,orgA.getId())).isEqualTo(1);
        // Exercise the supported local password-only identity through its authorized API.
        // Creation still defaults MFA on; no authentication/security toggle is changed.
        assertThat(sql.queryForObject("SELECT TwoFactorEnabled FROM Person.Profile WHERE RowGuid=?",Boolean.class,id)).isTrue();
        assertThat(call(platform,"PUT",path+"/security-settings","{\"twoFactorEnabled\":false}").statusCode()).isEqualTo(200);
        localLogin("local-http-created","Zq8!vR6@kN4$",brand,true);
        var updates=new LinkedHashMap<String,Map<String,Object>>();
        updates.put("profile",Map.of("branding",brand,"intent","idp","metadata",Map.of(),"firstName","Updated","lastName","Local","phoneNumber","+18888888888"));
        updates.put("email",Map.of("branding",brand,"intent","idp","metadata",Map.of(),"email","updated-local@example.test"));
        updates.put("password",Map.of("branding",brand,"intent","idp","metadata",Map.of(),"password",Base64.getEncoder().encodeToString("Wn7!pT9@jD3%".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        updates.put("username",Map.of("branding",brand,"intent","idp","metadata",Map.of(),"username","local-http-renamed"));
        for(var entry:updates.entrySet()) {
            String body=mapper.writeValueAsString(entry.getValue());
            var state=com.chellavignesh.authserver.support.ManagementObservations.snapshot(sql);
            byte[] passwordBefore=sql.queryForObject("SELECT c.Password FROM Person.Credential c JOIN Person.Profile p ON p.ProfileId=c.ProfileId WHERE p.RowGuid=?",byte[].class,id);
            var deniedResponse=call(denied,"PUT",path+"/"+entry.getKey(),body);
            assertThat(deniedResponse.statusCode()).as(entry.getKey()+" "+(entry.getKey().equals("profile")?deniedResponse.body():"denial")).isEqualTo(403);
            byte[] passwordAfter=sql.queryForObject("SELECT c.Password FROM Person.Credential c JOIN Person.Profile p ON p.ProfileId=c.ProfileId WHERE p.RowGuid=?",byte[].class,id);
            assertThat(Arrays.equals(passwordBefore,passwordAfter)).isTrue();
            assertThat(com.chellavignesh.authserver.support.ManagementObservations.snapshot(sql)).isEqualTo(state);
            var changed=call(platform,"PUT",path+"/"+entry.getKey(),body);
            assertThat(changed.statusCode()).as(entry.getKey()+" "+changed.body()).isEqualTo(Set.of("profile","email").contains(entry.getKey())?200:204);
        }
        assertThat(sql.queryForObject("SELECT FirstName FROM Person.Profile WHERE RowGuid=?",String.class,id)).isEqualTo("Updated");
        assertThat(sql.queryForObject("SELECT Email FROM Person.Profile WHERE RowGuid=?",String.class,id)).isEqualTo("updated-local@example.test");
        assertThat(sql.queryForObject("SELECT c.UserName FROM Person.Credential c JOIN Person.Profile p ON p.ProfileId=c.ProfileId WHERE p.RowGuid=?",String.class,id)).isEqualTo("local-http-renamed");
        localLogin("local-http-renamed","Wn7!pT9@jD3%",brand,true);
        localLogin("local-http-renamed","Zq8!vR6@kN4$",brand,false);
    }

    @Test void profileUpdateKeepsIdentityBindingsAndSuffix() throws Exception {
        String brand=localBrand(),name="local-identity-"+UUID.randomUUID().toString().substring(0,8);
        var created=call(platform,"POST","/api/v1/users",createBody(orgA.getRowGuid(),null,name,brand));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String id=mapper.readTree(created.body()).path("id").asText(),path="/api/v1/users/"+id;
        String login="SELECT CONVERT(VARCHAR(36),c.RowGuid) FROM Person.Credential c JOIN Person.Profile p ON p.ProfileId=c.ProfileId WHERE p.RowGuid=?";
        String loginBefore=sql.queryForObject(login,String.class,id);
        var suffixed=call(platform,"PUT",path+"/profile",mapper.writeValueAsString(Map.ofEntries(
                Map.entry("branding",brand),Map.entry("intent","idp"),Map.entry("metadata",Map.of()),
                Map.entry("firstName","Suffix"),Map.entry("lastName","Kept"),
                Map.entry("phoneNumber","+18888888888"),Map.entry("suffix","Mrs"))));
        assertThat(suffixed.statusCode()).as(suffixed.body()).isEqualTo(200);
        Integer suffix=sql.queryForObject("SELECT Suffix FROM Person.Profile WHERE RowGuid=?",Integer.class,id);
        assertThat(suffix).isNotNull();
        // Supplied identity bindings must be ignored; Person.UpdateUserProfile rewrites RowGuids from
        // them, which would orphan this login and any session bound to it.
        String foreign=UUID.randomUUID().toString();
        var rebound=call(platform,"PUT",path+"/profile",mapper.writeValueAsString(Map.ofEntries(
                Map.entry("branding",brand),Map.entry("intent","idp"),Map.entry("metadata",Map.of()),
                Map.entry("firstName","Rebound"),Map.entry("lastName","Attempt"),Map.entry("phoneNumber","+18888888888"),
                Map.entry("memberId",foreign),Map.entry("loginId",foreign))));
        assertThat(rebound.statusCode()).as(rebound.body()).isEqualTo(200);
        assertThat(sql.queryForObject("SELECT CONVERT(VARCHAR(36),RowGuid) FROM Person.Profile WHERE RowGuid=?",String.class,id)).isEqualToIgnoringCase(id);
        assertThat(sql.queryForObject(login,String.class,id)).isEqualTo(loginBefore);
        assertThat(sql.queryForObject("SELECT Suffix FROM Person.Profile WHERE RowGuid=?",Integer.class,id)).isEqualTo(suffix);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.Profile WHERE RowGuid=?",Integer.class,foreign)).isZero();
        assertThat(sql.queryForObject("SELECT FirstName FROM Person.Profile WHERE RowGuid=?",String.class,id)).isEqualTo("Rebound");
    }

    @Test void metadataColumnLimitsAndSystemManagedRowsAreEnforced() throws Exception {
        String brand=localBrand(),name="local-metadata-"+UUID.randomUUID().toString().substring(0,8);
        var created=call(platform,"POST","/api/v1/users",createBody(orgA.getRowGuid(),null,name,brand));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String id=mapper.readTree(created.body()).path("id").asText(),path="/api/v1/users/"+id+"/metadata";
        String longKey=mapper.writeValueAsString(Map.of("metaData",Map.of("k".repeat(31),"value")));
        String longValue=mapper.writeValueAsString(Map.of("metaData",Map.of("k","v".repeat(129))));
        assertThat(call(platform,"PUT",path,longKey).statusCode()).isEqualTo(400);
        assertThat(call(platform,"PUT",path,longValue).statusCode()).isEqualTo(400);
        assertThat(call(platform,"PUT",path,mapper.writeValueAsString(Map.of("metaData",Map.of("","value")))).statusCode()).isEqualTo(400);
        sql.update("INSERT Person.Metadata(ProfileId,[Key],[Value],SystemManaged) SELECT ProfileId,'sys.key','system',1 FROM Person.Profile WHERE RowGuid=?",id);
        sql.update("INSERT Person.Metadata(ProfileId,[Key],[Value],SystemManaged) SELECT ProfileId,'old.key','old',0 FROM Person.Profile WHERE RowGuid=?",id);
        var accepted=call(platform,"PUT",path,mapper.writeValueAsString(Map.of("metaData",Map.of("user.key","kept","sys.key","tampered"))));
        assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(200);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.Metadata m JOIN Person.Profile p ON p.ProfileId=m.ProfileId WHERE p.RowGuid=? AND m.[Key]='sys.key' AND m.[Value]='system' AND m.SystemManaged=1",Integer.class,id)).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.Metadata m JOIN Person.Profile p ON p.ProfileId=m.ProfileId WHERE p.RowGuid=? AND m.[Key]='user.key' AND m.[Value]='kept' AND m.SystemManaged=0",Integer.class,id)).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.Metadata m JOIN Person.Profile p ON p.ProfileId=m.ProfileId WHERE p.RowGuid=? AND m.[Key]='old.key'",Integer.class,id)).isZero();
    }

    @Test void userStatusChangeRequiresDeleteAuthority() throws Exception {
        String brand=localBrand(),name="local-status-"+UUID.randomUUID().toString().substring(0,8);
        var created=call(platform,"POST","/api/v1/users",createBody(orgA.getRowGuid(),null,name,brand));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String id=mapper.readTree(created.body()).path("id").asText();
        var updateOnly=token(user(orgA,group(orgA,"status-update-"+UUID.randomUUID(),List.of("idp-admin-user:read","idp-admin-user:update")),"status-update-"+UUID.randomUUID()));
        var state=com.chellavignesh.authserver.support.ManagementObservations.snapshot(sql);
        var denied=call(updateOnly,"PUT","/api/v1/users/"+id+"/status",mapper.writeValueAsString(Map.of("status","Inactive")));
        assertThat(denied.statusCode()).as(denied.body()).isEqualTo(403);
        assertThat(com.chellavignesh.authserver.support.ManagementObservations.snapshot(sql)).isEqualTo(state);
        var deletable=token(user(orgA,group(orgA,"status-delete-"+UUID.randomUUID(),List.of("idp-admin-user:read","idp-admin-user:delete")),"status-delete-"+UUID.randomUUID()));
        var changed=call(deletable,"PUT","/api/v1/users/"+id+"/status",mapper.writeValueAsString(Map.of("status","Inactive")));
        assertThat(changed.statusCode()).as(changed.body()).isEqualTo(204);
        assertThat(sql.queryForObject("SELECT Status FROM Person.Profile WHERE RowGuid=?",Integer.class,id)).isZero();
    }
    void localLogin(String username,String password,String brand,boolean succeeds) throws Exception {
        var browser=java.net.http.HttpClient.newBuilder().cookieHandler(new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ALL)).followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest("owned-local-pkce-verifier-012345678901234567890123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String authorize="/oauth2/authorize?response_type=code&client_id="+app.getClientId()+"&redirect_uri="+java.net.URLEncoder.encode("http://localhost/callback",java.nio.charset.StandardCharsets.UTF_8)+"&scope=openid&code_challenge_method=S256&code_challenge="+challenge+"&branding="+brand+"&state=owned-state&x-request-datetime="+java.net.URLEncoder.encode(java.time.Instant.now().toString(),java.nio.charset.StandardCharsets.UTF_8);
        var start=browserRequest(browser,"GET",authorize,null);
        assertThat(start.statusCode()).isEqualTo(302);
        var login=browserRequest(browser,"GET",start.headers().firstValue("Location").orElseThrow(),null);
        assertThat(login.statusCode()).isEqualTo(200);
        var csrf=java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(login.body());
        assertThat(csrf.find()).isTrue();
        var values=Map.of("username",username,"password",Base64.getEncoder().encodeToString(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"_csrf",csrf.group(1));
        String form=values.entrySet().stream().map(e->java.net.URLEncoder.encode(e.getKey(),java.nio.charset.StandardCharsets.UTF_8)+"="+java.net.URLEncoder.encode(e.getValue(),java.nio.charset.StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
        var authenticated=browserRequest(browser,"POST","/login",form);
        assertThat(authenticated.statusCode()).isEqualTo(302);
        assertThat(authenticated.headers().firstValue("Location").orElseThrow().contains("error")).isEqualTo(!succeeds);
        if(succeeds) {
            var authorized=browserRequest(browser,"GET",authenticated.headers().firstValue("Location").orElseThrow(),null);
            assertThat(authorized.statusCode()).isEqualTo(302);
            assertThat(authorized.headers().firstValue("Location").orElseThrow()).startsWith("http://localhost/callback?").contains("code=","state=owned-state");
        }
    }
    java.net.http.HttpResponse<String> browserRequest(java.net.http.HttpClient browser,String method,String path,String form) throws Exception {
        String url=path.startsWith("http")?path:"http://localhost:"+port+(path.startsWith("/services")?path:"/services"+path);
        var destination=java.net.URI.create(url);
        assertThat(destination.getHost()).isIn("localhost","127.0.0.1");
        url="http://localhost:"+port+destination.getRawPath()+(destination.getRawQuery()==null?"":"?"+destination.getRawQuery());
        var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).header("User-Agent","PolicyIntegration/1.0").header("Accept-Language","en-US").header("x-request-datetime",java.time.Instant.now().toString());
        if(form!=null) request.header("Content-Type","application/x-www-form-urlencoded");
        return browser.send(request.method(method,form==null?java.net.http.HttpRequest.BodyPublishers.noBody():java.net.http.HttpRequest.BodyPublishers.ofString(form)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
    }

}
