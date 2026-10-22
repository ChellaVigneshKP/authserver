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
        var reader = token(user(orgA, 0, "acceptance-no-management-grants"));
        String path = "/api/v1/users/"+target.getRowGuid();
        for (String method : List.of("PUT", "DELETE")) {
            var before = com.chellavignesh.authserver.support.ManagementObservations.snapshot(sql);
            assertThat(call(reader, method, path+(method.equals("PUT")?"/security-settings":""),
                    method.equals("PUT")?"{\"twoFactorEnabled\":true}":null).statusCode()).isEqualTo(403);
            assertThat(com.chellavignesh.authserver.support.ManagementObservations.snapshot(sql)).isEqualTo(before);
        }
        assertThat(call(platform, "PUT", path+"/security-settings", "{\"twoFactorEnabled\":true}").statusCode()).isEqualTo(200);
        assertThat(sql.queryForObject("SELECT TwoFactorEnabled FROM Person.Profile WHERE ProfileId=?", Boolean.class, target.getId())).isTrue();
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
}
