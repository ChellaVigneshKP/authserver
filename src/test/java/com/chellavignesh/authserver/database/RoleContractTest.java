package com.chellavignesh.authserver.database;

import com.chellavignesh.authserver.support.ManagementHttpTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Real signed HTTP requests against an exclusively owned SQL Server database. */
@EnabledIfEnvironmentVariable(named="AUTH_APP_IT", matches="true")
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "app.local.bootstrap.enabled=false", "toggles.signature.required=true",
        "logging.level.root=WARN", "logback.access.enabled=false"})
class RoleContractTest extends ManagementHttpTestSupport {
    static final OwnedDatabase database=new OwnedDatabase();
    @DynamicPropertySource static void isolate(DynamicPropertyRegistry registry) { database.register(registry); }
    Fixture platform,tenant;
    String path;
    @BeforeAll void fixtures() throws Exception {
        orgA=organization("Roles A"); orgB=organization("Roles B"); app=application(orgA);
        redirects.createRedirectUris(orgA.getId(),app.getId(),List.of("http://localhost/callback"));
        platform=token(user(organizations.getById(1).orElseThrow(),1,"roles-platform"));
        int role=group(orgA,"Tenant forged writer",List.of("idp-admin-group:read","idp-admin-group:create","idp-admin-group:update","idp-admin-group:delete","idp-admin-permission:read"));
        tenant=token(user(orgA,role,"roles-tenant"));
        path="/api/v1/organizations/"+orgA.getRowGuid()+"/groups";
    }
    @AfterAll void cleanup() { cleanup(database); }
    String body(String name,List<String> keys,Integer revision) throws Exception {
        var dto=new LinkedHashMap<String,Object>();dto.put("name",name);dto.put("description","Role fixture");dto.put("permissionKeys",keys);
        if(revision!=null)dto.put("revision",revision);return mapper.writeValueAsString(dto);
    }
    JsonNode create(String name,List<String> keys) throws Exception {
        var response=call(platform,"POST",path,body(name,keys,null));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);signature(platform,response);
        return mapper.readTree(response.body());
    }
    void problem(HttpResponse<String> response,int status) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        var data=mapper.readTree(response.body());assertThat(data.path("code").asText()).isNotBlank();
        assertThat(data.path("message").asText()).isNotBlank();assertThat(data.path("requestId").asText()).isEqualTo("fixture-request-123");
        assertThat(data.path("retryable").asBoolean()).isFalse();
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        signature(platform,response);
    }
    void signature(Fixture token,HttpResponse<String> response) throws Exception {
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(token.key(),"HmacSHA256"));
        assertThat(response.headers().firstValue("x-signature").orElse("")).isEqualTo(Base64.getEncoder().encodeToString(mac.doFinal(response.body().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
    @Test void roleCreateRejectsUnknownOrDuplicateKeys() throws Exception {
        for(var keys:List.of(List.of("arbitrary:write"),List.of("idp-admin-user:read","idp-admin-user:read"),List.<String>of()))
            problem(call(platform,"POST",path,body("invalid-"+UUID.randomUUID(),keys,null)),400);
    }
    @Test void customRolesCannotContainPlatformReservedActions() throws Exception {
        for(String key:List.of("idp-admin-org:switch","idp-admin-group:create","idp-admin-permission:read","idp-admin-resource:read","idp-admin-resource:update","idp-admin-app:assign-resource","idp-admin-org-cert:create"))
            problem(call(platform,"POST",path,body("reserved-"+UUID.randomUUID(),List.of(key),null)),400);
    }
    @Test void customRoleNamesAreCaseInsensitivePerOrg() throws Exception {
        var role=create("  Unique Custom Name  ",List.of("idp-admin-user:read"));assertThat(role.path("name").asText()).isEqualTo("Unique Custom Name");
        problem(call(platform,"POST",path,body("unique custom name",List.of("idp-admin-user:read"),null)),409);
        var other=call(platform,"POST","/api/v1/organizations/"+orgB.getRowGuid()+"/groups",body("unique custom name",List.of("idp-admin-user:read"),null));
        assertThat(other.statusCode()).isEqualTo(201);
    }
    @Test void builtinRolesAreImmutable() throws Exception {
        String id=sql.queryForObject("SELECT CONVERT(VARCHAR(36),RowGuid) FROM Partner.OrganizationGroup WHERE OrganizationId=? AND GroupTemplateId=1",String.class,orgA.getId());
        problem(call(platform,"PUT",path+"/"+id,body("Rewrite",List.of("idp-admin-user:read"),1)),409);
        problem(call(platform,"DELETE",path+"/"+id,null),409);
    }
    @Test void tenantCannotEditRoleDefinitions() throws Exception {
        var role=create("Tenant denied",List.of("idp-admin-user:read"));String item=path+"/"+role.path("id").asText();
        for(var response:List.of(call(tenant,"POST",path,body("denied",List.of("idp-admin-user:read"),null)),call(tenant,"PUT",item,body("denied",List.of("idp-admin-user:read"),1)),call(tenant,"DELETE",item,null))) {
            assertThat(response.statusCode()).isEqualTo(403);signature(tenant,response);
        }
        assertThat(call(tenant,"GET",item,null).statusCode()).isEqualTo(200);
    }
    @Test void platformStillNeedsExplicitRoleAction() throws Exception {
        var organization=organizations.getById(1).orElseThrow();
        int group=group(organization,"Platform reader only",List.of("idp-admin-org:switch","idp-admin-group:read"));
        var reader=token(user(organization,group,"platform-read-only"));
        var response=call(reader,"POST",path,body("Must be denied",List.of("idp-admin-user:read"),null));
        assertThat(response.statusCode()).isEqualTo(403);signature(reader,response);
    }
    @Test void staleRoleRevisionReturns409() throws Exception {
        var role=create("Revision",List.of("idp-admin-user:read"));String item=path+"/"+role.path("id").asText();
        assertThat(role.path("revision").asInt()).isEqualTo(1);
        var updated=call(platform,"PUT",item,body("Revision changed",List.of("idp-admin-app:read"),1));assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(updated.body()).path("revision").asInt()).isEqualTo(2);
        problem(call(platform,"PUT",item,body("Stale",List.of("idp-admin-user:read"),1)),409);
        create("Revision collision",List.of("idp-admin-user:read"));
        problem(call(platform,"PUT",item,body("revision COLLISION",List.of("idp-admin-user:read"),2)),409);
        assertThat(mapper.readTree(call(platform,"GET",item,null).body()).path("name").asText()).isEqualTo("Revision changed");
    }
    @Test void concurrentRoleRevisionsHaveExactlyOneWinner() throws Exception {
        var role=create("Race",List.of("idp-admin-user:read"));String item=path+"/"+role.path("id").asText();
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Integer> update=()->{start.await();return call(platform,"PUT",item,body("Raced",List.of("idp-admin-app:read"),1)).statusCode();};
            var one=executor.submit(update);var two=executor.submit(update);start.countDown();
            assertThat(List.of(one.get(30,TimeUnit.SECONDS),two.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
    }
    @Test void assignedRoleCannotBeDeactivated() throws Exception {
        var role=create("Assigned",List.of("idp-admin-user:read"));String id=role.path("id").asText();
        int numeric=sql.queryForObject("SELECT OrganizationGroupId FROM Partner.OrganizationGroup WHERE RowGuid=?",Integer.class,id);
        user(orgA,numeric,"role-assigned");var rejected=call(platform,"DELETE",path+"/"+id,null);problem(rejected,409);
        assertThat(mapper.readTree(rejected.body()).path("code").asText()).isEqualTo("ROLE_ASSIGNED");
        assertThat(mapper.readTree(rejected.body()).path("message").asText()).contains("1 assigned member");
        assertThat(mapper.readTree(call(platform,"GET",path+"/"+id,null).body()).path("memberCount").asInt()).isEqualTo(1);
    }
    @Test void foreignRoleIsNotFound() throws Exception {
        var role=create("Foreign",List.of("idp-admin-user:read"));String foreign="/api/v1/organizations/"+orgB.getRowGuid()+"/groups/"+role.path("id").asText();
        problem(call(platform,"GET",foreign,null),404);problem(call(platform,"PUT",foreign,body("Foreign",List.of("idp-admin-user:read"),1)),404);problem(call(platform,"DELETE",foreign,null),404);
    }
    @Test void roleChangeUpdatesExistingTokens() throws Exception {
        var role=create("Revocable",List.of("idp-admin-group:read"));String item=path+"/"+role.path("id").asText();
        int id=sql.queryForObject("SELECT OrganizationGroupId FROM Partner.OrganizationGroup WHERE RowGuid=?",Integer.class,role.path("id").asText());
        var oldToken=token(user(orgA,id,"role-revocable"));assertThat(call(oldToken,"GET",path,null).statusCode()).isEqualTo(200);
        assertThat(call(platform,"PUT",item,body("Revocable",List.of("idp-admin-user:read"),1)).statusCode()).isEqualTo(200);
        assertThat(call(oldToken,"GET",path,null).statusCode()).isEqualTo(403);
    }
    @Test void validationAndRollbackPreserveRole() throws Exception {
        for(String name:List.of(" ","x".repeat(101),"invalid\nname"))problem(call(platform,"POST",path,body(name,List.of("idp-admin-user:read"),null)),400);
        var oversized=mapper.createObjectNode().put("name","Oversize description").put("description","x".repeat(1025));oversized.putArray("permissionKeys").add("idp-admin-user:read");
        problem(call(platform,"POST",path,oversized.toString()),400);
        var role=create("Rollback",List.of("idp-admin-user:read"));String item=path+"/"+role.path("id").asText();
        problem(call(platform,"PUT",item,body("Should rollback",List.of("unknown:key"),1)),400);
        assertThat(mapper.readTree(call(platform,"GET",item,null).body())).isEqualTo(role);
    }
    @Test void deactivateIsSoftAndKeepsIds() throws Exception {
        var role=create("Deactivate",List.of("idp-admin-user:read"));String item=path+"/"+role.path("id").asText();
        assertThat(call(platform,"DELETE",item,null).statusCode()).isEqualTo(204);
        var result=mapper.readTree(call(platform,"GET",item,null).body());assertThat(result.path("status").asText()).isEqualTo("Inactive");assertThat(result.path("revision").asInt()).isEqualTo(2);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Partner.OrganizationGroup WHERE RowGuid=?",Integer.class,role.path("id").asText())).isEqualTo(1);
    }
    @Test void catalogIsPlatformOnlyAndClassifiesResourceReads() throws Exception {
        var result=call(platform,"GET","/api/v1/portal/permissions",null);assertThat(result.statusCode()).isEqualTo(200);signature(platform,result);
        var entries=mapper.readTree(result.body());assertThat(entries).isNotEmpty();
        assertThat(call(platform,"GET","/api/v1/endpoints",null).statusCode()).isEqualTo(200);
        var resources=new ArrayList<JsonNode>();entries.forEach(entry->{if(entry.path("key").asText().startsWith("idp-admin-resource:"))resources.add(entry);});
        assertThat(resources).hasSize(4).allSatisfy(entry->assertThat(entry.path("platformOnly").asBoolean()).isTrue());
        assertThat(call(tenant,"GET","/api/v1/portal/permissions",null).statusCode()).isEqualTo(403);
    }
    @Test void legacyTenantResourceGrantCannotReadOrWritePlatformCatalog() throws Exception {
        int group=group(orgA,"Legacy resource reader",List.of("idp-admin-resource:read","idp-admin-resource:create","idp-admin-resource:update"));
        var bearer=token(user(orgA,group,"legacy-resources"));
        assertThat(call(bearer,"GET","/api/v1/endpoints",null).statusCode()).isEqualTo(403);
        assertThat(call(bearer,"POST","/api/v1/endpoints","{\"name\":\"Denied\",\"description\":\"Denied\",\"uri\":\"https://example.test/denied\",\"allowedMethod\":\"GET\",\"urn\":\"urn:test:denied\"}").statusCode()).isEqualTo(403);
        assertThat(call(bearer,"PUT","/api/v1/endpoints/"+UUID.randomUUID(),"{\"name\":\"Denied\",\"description\":\"Denied\",\"uri\":\"https://example.test/denied\",\"allowedMethod\":\"GET\",\"urn\":\"urn:test:denied\"}").statusCode()).isEqualTo(403);
    }
    @Test void existingListHasRevisionAndMemberCount() throws Exception {
        var list=mapper.readTree(call(tenant,"GET",path,null).body());assertThat(list).isNotEmpty();
        for(var role:list) {assertThat(role.path("revision").asInt()).isPositive();assertThat(role.has("memberCount")).isTrue();}
    }
    @Test void deliberateBuiltinGrantsReachTenantAdministratorWithoutPlatformAuthority() throws Exception {
        int group=sql.queryForObject("SELECT OrganizationGroupId FROM Partner.OrganizationGroup WHERE OrganizationId=? AND GroupTemplateId=1",Integer.class,orgA.getId());
        var bearer=token(user(orgA,group,"seeded-admin"));
        var response=call(bearer,"GET","/api/v1/portal/context",null);assertThat(response.statusCode()).isEqualTo(200);
        var keys=new ArrayList<String>();mapper.readTree(response.body()).path("permissions").forEach(item->keys.add(item.asText()));
        assertThat(keys).contains("idp-admin-user:create","idp-admin-user:read","idp-admin-user:update","idp-admin-user:delete","idp-admin-group:assign");
        assertThat(keys).doesNotContain("idp-admin-org:switch","idp-admin-permission:read","idp-admin-group:create","idp-admin-resource:read");
    }
    @Test void failedPermissionReplacementRollsBackNameRevisionAndGrants() throws Exception {
        var role=create("Transactional",List.of("idp-admin-user:read"));String item=path+"/"+role.path("id").asText();
        int id=sql.queryForObject("SELECT OrganizationGroupId FROM Partner.OrganizationGroup WHERE RowGuid=?",Integer.class,role.path("id").asText());
        sql.execute("CREATE TRIGGER Partner.RoleFixtureFailure ON Partner.OrganizationGroupPermission AFTER INSERT AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE OrganizationGroupId="+id+") THROW 51013,'Owned fixture failure',1; END");
        try {
            problem(call(platform,"PUT",item,body("Should rollback transaction",List.of("idp-admin-app:read"),1)),500);
            assertThat(mapper.readTree(call(platform,"GET",item,null).body())).isEqualTo(role);
        } finally { sql.execute("DROP TRIGGER Partner.RoleFixtureFailure"); }
    }
    @Test void sqlApplicationLockIsCrossConnectionAndTimeoutIsRetryable() throws Exception {
        try(var connection=sql.getDataSource().getConnection();var statement=connection.createStatement()) {
            statement.execute("BEGIN TRANSACTION; EXEC sys.sp_getapplock @Resource='authserver:identity-management:v1',@LockMode='Exclusive',@LockOwner='Transaction';");
            try {
                var response=call(platform,"POST",path,body("Locked externally",List.of("idp-admin-user:read"),null));
                assertThat(response.statusCode()).as(response.body()).isEqualTo(503);signature(platform,response);
                var error=mapper.readTree(response.body());assertThat(error.path("retryable").asBoolean()).isTrue();
                assertThat(error.path("code").asText()).isEqualTo("SERVICE_UNAVAILABLE");
                assertThat(response.headers().firstValue("Retry-After").orElse("")).isEqualTo("5");
                assertThat(sql.queryForObject("SELECT COUNT(*) FROM Partner.OrganizationGroup WHERE GroupName='Locked externally'",Integer.class)).isZero();
            } finally { statement.execute("ROLLBACK TRANSACTION"); }
        }
    }
    @Test void missingBearerHasSafeUnauthorizedContract() throws Exception {
        for(String method:List.of("GET","POST")) {
            var response=call(null,method,method.equals("GET")?path+"/"+UUID.randomUUID():path,method.equals("GET")?null:body("Denied",List.of("idp-admin-user:read"),null));
            assertThat(response.statusCode()).isEqualTo(401);assertThat(mapper.readTree(response.body()).path("code").asText()).isEqualTo("UNAUTHORIZED");
            assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        }
    }
    @Test void incompatibleNamesAndMembershipsBlockMutationsWithoutRepair() throws Exception {
        int one=group(orgB,"Duplicate",List.of("idp-admin-user:read"));int two=group(orgB,"duplicate",List.of("idp-admin-user:read"));
        try {
            String other="/api/v1/organizations/"+orgB.getRowGuid()+"/groups";
            problem(call(platform,"POST",other,body("Blocked",List.of("idp-admin-user:read"),null)),409);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM Partner.OrganizationGroup WHERE OrganizationId=? AND LOWER(GroupName)='duplicate'",Integer.class,orgB.getId())).isEqualTo(2);
        } finally { sql.update("UPDATE Partner.OrganizationGroup SET GroupName='Resolved fixture' WHERE OrganizationGroupId=?",two); }
        int local=group(orgA,"Ambiguous second",List.of("idp-admin-user:read"));
        var member=user(orgA,local,"role-ambiguous");
        sql.update("INSERT Person.ProfileGroup(ProfileId,OrganizationGroupId) VALUES(?,?)",member.getId(),one);
        try {
            problem(call(platform,"POST",path,body("Membership blocked",List.of("idp-admin-user:read"),null)),409);
        } finally { sql.update("DELETE Person.ProfileGroup WHERE ProfileId=? AND OrganizationGroupId=?",member.getId(),one); }
    }
}
