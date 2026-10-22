package com.chellavignesh.authserver.database;

import com.chellavignesh.authserver.support.ManagementHttpTestSupport;
import com.chellavignesh.authserver.adminportal.user.entity.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.mockito.Mockito;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="AUTH_APP_IT", matches="true")
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "app.local.bootstrap.enabled=false", "toggles.signature.required=true",
        "logging.level.root=WARN", "logback.access.enabled=false"})
class RoleAssignmentContractTest extends ManagementHttpTestSupport {
    static final OwnedDatabase database=new OwnedDatabase();
    @DynamicPropertySource static void isolate(DynamicPropertyRegistry registry) { database.register(registry); }
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.chellavignesh.authserver.security.ManagementMutationLock mutationLock;
    Fixture platform;
    User platformUser;
    @BeforeAll void fixtures() throws Exception {
        orgA=organization("Assignments A"); orgB=organization("Assignments B"); app=application(orgA);
        redirects.createRedirectUris(orgA.getId(),app.getId(),List.of("http://localhost/callback"));
        platformUser=user(organizations.getById(1).orElseThrow(),1,"assignment-platform");platform=token(platformUser);
    }
    @AfterAll void cleanup() { cleanup(database); }
    int role(com.chellavignesh.authserver.adminportal.organization.entity.Organization org,List<String> keys) {
        return group(org,"fixture-"+UUID.randomUUID(),keys);
    }
    UUID guid(int role) { return UUID.fromString(sql.queryForObject("SELECT CONVERT(VARCHAR(36),RowGuid) FROM Partner.OrganizationGroup WHERE OrganizationGroupId=?",String.class,role)); }
    User member(com.chellavignesh.authserver.adminportal.organization.entity.Organization org,int role) throws Exception {return user(org,role,"fixture-"+UUID.randomUUID());}
    String route(com.chellavignesh.authserver.adminportal.organization.entity.Organization org,User user) {return "/api/v1/organizations/"+org.getRowGuid()+"/users/"+user.getRowGuid()+"/group";}
    String body(Integer role) {return "{\"groupId\":"+(role==null?"null":"\""+guid(role)+"\"")+"}";}
    List<Integer> memberships(User user) {return sql.queryForList("SELECT OrganizationGroupId FROM Person.ProfileGroup WHERE ProfileId=? ORDER BY OrganizationGroupId",Integer.class,user.getId());}
    void status(User user,int value) {sql.update("UPDATE Person.Profile SET Status=? WHERE ProfileId=?",value,user.getId());}
    @Test void assignmentCannotCrossOrg() throws Exception {
        var target=member(orgA,0);int foreign=role(orgB,List.of("idp-admin-user:read"));
        assertThat(call(platform,"PUT",route(orgA,target),body(foreign)).statusCode()).isEqualTo(404);
        var foreignUser=call(platform,"PUT",route(orgB,target),body(foreign));
        assertThat(foreignUser.statusCode()).isEqualTo(404);
        var absent=new User();absent.setRowGuid(UUID.randomUUID());
        var missingUser=call(platform,"PUT",route(orgB,absent),body(foreign));
        assertThat(missingUser.statusCode()).isEqualTo(404);
        assertThat(mapper.readTree(missingUser.body()).path("code").asText()).isEqualTo(mapper.readTree(foreignUser.body()).path("code").asText());
        assertThat(foreignUser.body()).doesNotContain(target.getRowGuid().toString(),"ProfileId");
        assertThat(memberships(target)).isEmpty();
    }
    @Test void tenantCannotGrantBeyondOwnPermissions() throws Exception {
        int limited=role(orgA,List.of("idp-admin-group:assign","idp-admin-user:update"));
        var actor=token(member(orgA,limited));var target=member(orgA,0);int elevated=role(orgA,List.of("idp-admin-app:delete"));
        assertThat(call(actor,"PUT",route(orgA,target),body(elevated)).statusCode()).isEqualTo(403);
        assertThat(memberships(target)).isEmpty();
        assertThat(call(actor,"PUT",route(orgA,target),body(limited)).statusCode()).isEqualTo(204);
        assertThat(memberships(target)).containsExactly(limited);
    }
    @Test void selfElevationDenied() throws Exception {
        int limited=role(orgA,List.of("idp-admin-group:assign"));var actor=member(orgA,limited);
        int high=role(orgA,List.of("idp-admin-group:assign","idp-admin-user:delete"));
        assertThat(call(token(actor),"PUT",route(orgA,actor),body(high)).statusCode()).isEqualTo(403);
        assertThat(memberships(actor)).containsExactly(limited);
    }
    @Test void platformGroupCannotBeAssignedByTenant() throws Exception {
        int limited=role(orgA,List.of("idp-admin-group:assign","idp-admin-org:switch"));
        // A tenant cannot become a platform actor through an imported switch grant.
        var actor=token(member(orgA,limited));var target=member(orgA,0);
        assertThat(call(actor,"PUT",route(orgA,target),body(limited)).statusCode()).isEqualTo(403);
        assertThat(memberships(target)).isEmpty();
    }
    @Test void unassignLeavesUserActive() throws Exception {
        int initial=role(orgA,List.of("idp-admin-group:read"));var target=member(orgA,initial);var retained=token(target);
        assertThat(call(retained,"GET","/api/v1/organizations/"+orgA.getRowGuid()+"/groups",null).statusCode()).isEqualTo(200);
        assertThat(call(platform,"PUT",route(orgA,target),body(null)).statusCode()).isEqualTo(204);
        assertThat(memberships(target)).isEmpty();
        assertThat(sql.queryForObject("SELECT Status FROM Person.Profile WHERE ProfileId=?",Integer.class,target.getId())).isEqualTo(1);
        assertThat(call(retained,"GET","/api/v1/organizations/"+orgA.getRowGuid()+"/groups",null).statusCode()).isEqualTo(403);
    }
    @Test void legacyMultipleGroupsReturns409() throws Exception {
        int first=role(orgB,List.of("idp-admin-user:read")),second=role(orgB,List.of("idp-admin-app:read"));var target=member(orgB,first);
        sql.update("INSERT Person.ProfileGroup(ProfileId,OrganizationGroupId) VALUES(?,?)",target.getId(),second);
        try {
            assertThat(call(platform,"PUT",route(orgB,target),body(null)).statusCode()).isEqualTo(409);
            assertThat(call(platform,"PUT","/api/v1/users/"+target.getRowGuid()+"/status","{\"status\":\"Inactive\"}").statusCode()).isEqualTo(409);
            assertThat(call(platform,"DELETE","/api/v1/users/"+target.getRowGuid(),null).statusCode()).isEqualTo(409);
            assertThat(memberships(target)).containsExactly(first,second);
            assertThat(sql.queryForObject("SELECT Status FROM Person.Profile WHERE ProfileId=?",Integer.class,target.getId())).isEqualTo(1);
        } finally {sql.update("DELETE Person.ProfileGroup WHERE ProfileId=? AND OrganizationGroupId=?",target.getId(),second);}
    }
    @Test void inactiveMembershipAndGroupLoseRawEvaluatorAuthority() throws Exception {
        int group=role(orgA,List.of("idp-admin-app:read"));var target=member(orgA,group);var bearer=token(target);
        String path="/api/v1/organizations/"+orgA.getRowGuid()+"/applications";
        assertThat(call(bearer,"GET",path,null).statusCode()).isEqualTo(200);
        sql.update("UPDATE Partner.OrganizationGroup SET Status=0 WHERE OrganizationGroupId=?",group);
        assertThat(call(bearer,"GET",path,null).statusCode()).isEqualTo(403);
        sql.update("UPDATE Partner.OrganizationGroup SET Status=1 WHERE OrganizationGroupId=?",group);
        sql.update("UPDATE Person.ProfileOrganization SET Status=0 WHERE ProfileId=?",target.getId());
        try {assertThat(call(bearer,"GET",path,null).statusCode()).isIn(401,403);}
        finally {sql.update("UPDATE Person.ProfileOrganization SET Status=1 WHERE ProfileId=?",target.getId());}
    }
    @Test void ambiguousActorCannotUseRawEvaluator() throws Exception {
        int group=role(orgA,List.of("idp-admin-app:read"));var target=member(orgA,group);var bearer=token(target);
        sql.update("INSERT Person.ProfileOrganization(ProfileId,OrganizationId) VALUES(?,?)",target.getId(),orgB.getId());
        try {assertThat(call(bearer,"GET","/api/v1/organizations/"+orgA.getRowGuid()+"/applications",null).statusCode()).isIn(401,403);}
        finally {sql.update("DELETE Person.ProfileOrganization WHERE ProfileId=? AND OrganizationId=?",target.getId(),orgB.getId());}
    }
    List<Integer> disableOthers(User... retained) {
        var ids=Arrays.stream(retained).map(User::getId).toList();
        var active=sql.queryForList("SELECT ProfileId FROM Person.Profile WHERE Status=1",Integer.class).stream().filter(id->!ids.contains(id)).toList();
        active.forEach(id->sql.update("UPDATE Person.Profile SET Status=0 WHERE ProfileId=?",id));return active;
    }
    void restore(List<Integer> ids) {ids.forEach(id->sql.update("UPDATE Person.Profile SET Status=1 WHERE ProfileId=?",id));}
    @Test void lastPlatformAdminCannotBeRemoved() throws Exception {
        var other=member(organizations.getById(1).orElseThrow(),1);sql.update("UPDATE Person.Credential SET Status=0 WHERE ProfileId=?",other.getId());
        var disabled=disableOthers(platformUser,other);
        try {
            String target="/api/v1/users/"+platformUser.getRowGuid();
            assertThat(call(platform,"PUT",target+"/status","{\"status\":\"Inactive\"}").statusCode()).isEqualTo(409);
            assertThat(call(platform,"DELETE",target,null).statusCode()).isEqualTo(409);
            assertThat(call(platform,"PUT",route(organizations.getById(1).orElseThrow(),platformUser),body(null)).statusCode()).isEqualTo(409);
            assertThat(sql.queryForObject("SELECT Status FROM Person.Profile WHERE ProfileId=?",Integer.class,platformUser.getId())).isEqualTo(1);
            assertThat(memberships(platformUser)).containsExactly(1);
        } finally {restore(disabled);status(platformUser,1);}
    }
    @Test void concurrentAdminDisableLeavesOneActive() throws Exception {
        var org=organizations.getById(1).orElseThrow();var one=member(org,1);var two=member(org,1);var a=token(one);var b=token(two);var disabled=disableOthers(one,two);
        var reached=new CountDownLatch(2);var release=new CountDownLatch(1);
        Mockito.doAnswer(invocation->{reached.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return invocation.callRealMethod();}).when(mutationLock).acquire();
        try(var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->call(a,"PUT","/api/v1/users/"+one.getRowGuid()+"/status","{\"status\":\"Inactive\"}").statusCode());
            var second=executor.submit(()->call(b,"PUT","/api/v1/users/"+two.getRowGuid()+"/status","{\"status\":\"Inactive\"}").statusCode());
            assertThat(reached.await(10,TimeUnit.SECONDS)).isTrue();release.countDown();
            assertThat(List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(204,409);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.Profile WHERE ProfileId IN (?,?) AND Status=1",Integer.class,one.getId(),two.getId())).isEqualTo(1);
        } finally {release.countDown();Mockito.reset(mutationLock);restore(disabled);status(one,1);status(two,1);}
    }
    @Test void rolePermissionEditCannotExceedActorCeiling() throws Exception {
        var platformOrg=organizations.getById(1).orElseThrow();
        int limited=role(platformOrg,List.of("idp-admin-org:switch","idp-admin-group:update","idp-admin-user:read"));
        var actor=token(member(platformOrg,limited));int target=role(orgA,List.of("idp-admin-user:read"));
        var response=call(actor,"PUT","/api/v1/organizations/"+orgA.getRowGuid()+"/groups/"+guid(target),
                "{\"name\":\"Ceiling edit\",\"permissionKeys\":[\"idp-admin-app:delete\"],\"revision\":1}");
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(sql.queryForList("SELECT PermissionKey FROM Partner.OrganizationGroupPermission WHERE OrganizationGroupId=? AND Status=1",String.class,target)).containsExactly("idp-admin-user:read");
    }
    @Test void assignmentAndRoleUpdateSerialize() throws Exception {
        int limited=role(orgA,List.of("idp-admin-group:assign","idp-admin-user:read"));
        var actor=token(member(orgA,limited));var target=member(orgA,0);int changing=role(orgA,List.of("idp-admin-user:read"));
        var reached=new CountDownLatch(1);var release=new CountDownLatch(1);var first=new java.util.concurrent.atomic.AtomicBoolean(true);
        Mockito.doAnswer(invocation->{if(first.compareAndSet(true,false)){reached.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();}return invocation.callRealMethod();}).when(mutationLock).acquire();
        try(var connection=Objects.requireNonNull(sql.getDataSource()).getConnection();var statement=connection.createStatement();var executor=Executors.newSingleThreadExecutor()) {
            statement.execute("BEGIN TRANSACTION; DECLARE @r INT; EXEC @r=sys.sp_getapplock @Resource=N'authserver:identity-management:v1',@LockMode='Exclusive',@LockOwner='Transaction';");
            var pending=executor.submit(()->call(actor,"PUT",route(orgA,target),body(changing)));
            try {
                assertThat(reached.await(10,TimeUnit.SECONDS)).isTrue();
                statement.executeUpdate("UPDATE Partner.OrganizationGroupPermission SET PermissionKey='idp-admin-app:delete' WHERE OrganizationGroupId="+changing);
                statement.execute("COMMIT TRANSACTION");release.countDown();
                assertThat(pending.get(20,TimeUnit.SECONDS).statusCode()).isEqualTo(403);
                assertThat(memberships(target)).isEmpty();
            } finally {release.countDown();if(!connection.getAutoCommit())connection.rollback();}
        } finally {release.countDown();Mockito.reset(mutationLock);}
    }

    String createBody(User existing, String branding) throws Exception {
        var data=new LinkedHashMap<String,Object>();
        data.put("firstName","Boundary");data.put("lastName","Fixture");data.put("username","new-"+UUID.randomUUID());
        data.put("email","fresh@example.test");data.put("phoneNumber","+19999999999");data.put("branding",branding);
        data.put("password",Base64.getEncoder().encodeToString("mQ8!vZ2$rH6@wK9".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        data.put("orgGuid",orgA.getRowGuid());data.put("memberId",existing.getRowGuid());data.put("syncFlag",false);
        return mapper.writeValueAsString(data);
    }
    String profileBody(User target, String branding) throws Exception {
        String schema="urn:ascensus:529:1.0:accountsync";
        var payload=Map.of("branding",branding,"intent","conversion","timestamp",java.time.Instant.now().toString(),"orgId",orgA.getRowGuid(),
                "credential",Map.of("loginId",UUID.randomUUID(),"username","fresh-"+UUID.randomUUID(),"passwordHash","001122","cIndex","0"),
                "profile",Map.of("memberId",target.getRowGuid(),"firstName","Boundary","lastName","Fixture","email","fresh@example.test","phoneNumber","+19999999999","idpSyncDttm",java.time.Instant.now().toString()),
                "metadata",Map.of("idp",List.of(),"unite",List.of()),"syncFlag",false);
        return mapper.writeValueAsString(Map.of("schema",schema,schema,payload));
    }
    @Test void existingProfileGuidCannotCreateDuplicate() throws Exception {
        var target=member(orgA,0);
        var before=sql.queryForObject("SELECT COUNT(*) FROM Person.Profile WHERE RowGuid=?",Integer.class,target.getRowGuid().toString());
        var response=call(platform,"POST","/api/v1/users",createBody(target,"idp"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.Profile WHERE RowGuid=?",Integer.class,target.getRowGuid().toString())).isEqualTo(before);
    }
    @Test void revokedMembershipCannotBeHiddenDuringEitherCreationPath() throws Exception {
        var target=member(orgA,0);status(target,0);
        sql.update("UPDATE Person.ProfileOrganization SET Status=0 WHERE ProfileId=?",target.getId());
        try {
            for(var request:List.of(Map.entry("/api/v1/users",createBody(target,"unknown-provider")),Map.entry("/api/v1/users/profile",profileBody(target,"unknown-provider")))) {
                var response=call(platform,"POST",request.getKey(),request.getValue());
                assertThat(response.statusCode()).as(request.getKey()+" "+response.body()).isEqualTo(403);
                assertThat(sql.queryForObject("SELECT Status FROM Person.Profile WHERE ProfileId=?",Integer.class,target.getId())).isZero();
                assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.Profile WHERE RowGuid=?",Integer.class,target.getRowGuid().toString())).isEqualTo(1);
            }
        } finally {sql.update("UPDATE Person.ProfileOrganization SET Status=1 WHERE ProfileId=?",target.getId());}
    }
    @Test void everyLegacyTargetMutationRejectsAmbiguityBeforeProviderWork() throws Exception {
        int a=role(orgA,List.of("idp-admin-user:read")),b=role(orgA,List.of("idp-admin-user:update"));var target=member(orgA,a);
        sql.update("INSERT Person.ProfileGroup(ProfileId,OrganizationGroupId) VALUES(?,?)",target.getId(),b);
        String password=Base64.getEncoder().encodeToString("mQ8!vZ2$rH6@wK9".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var bodies=new LinkedHashMap<String,String>();
        bodies.put("profile","{\"branding\":\"unknown-provider\",\"intent\":\"idp\",\"firstName\":\"Boundary\",\"lastName\":\"Fixture\"}");
        bodies.put("email","{\"branding\":\"unknown-provider\",\"email\":\"change@example.test\"}");
        bodies.put("password","{\"branding\":\"unknown-provider\",\"password\":\""+password+"\"}");
        bodies.put("username","{\"branding\":\"unknown-provider\",\"username\":\"changed-username\"}");
        bodies.put("security-settings","{\"twoFactorEnabled\":false}");
        bodies.put("metadata","{\"metaData\":{\"forbidden\":\"write\"}}");
        bodies.put("status","{\"status\":\"Inactive\"}");
        var profile=sql.queryForMap("SELECT * FROM Person.Profile WHERE ProfileId=?",target.getId());
        var credentials=sql.queryForList("SELECT * FROM Person.Credential WHERE ProfileId=?",target.getId());
        try {
            for(var entry:bodies.entrySet()) {
                var payload=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(entry.getValue());
                if(List.of("profile","email","password","username").contains(entry.getKey())) {
                    payload.put("intent","idp");payload.set("metadata",mapper.createObjectNode());
                }
                var response=call(platform,"PUT","/api/v1/users/"+target.getRowGuid()+"/"+entry.getKey(),mapper.writeValueAsString(payload));
                assertThat(response.statusCode()).as(entry.getKey()+" "+response.body()).isEqualTo(409);
                assertThat(mapper.readTree(response.body()).path("requestId").asText()).isEqualTo("fixture-request-123");
                assertThat(mapper.readTree(response.body()).path("retryable").isBoolean()).isTrue();
            }
            assertThat(call(platform,"DELETE","/api/v1/users/"+target.getRowGuid(),null).statusCode()).isEqualTo(409);
            assertThat(call(platform,"POST","/api/v1/users",createBody(target,"unknown-provider")).statusCode()).isEqualTo(409);
            assertThat(call(platform,"POST","/api/v1/users/profile",profileBody(target,"unknown-provider")).statusCode()).isEqualTo(409);
            assertThat(sql.queryForMap("SELECT * FROM Person.Profile WHERE ProfileId=?",target.getId())).isEqualTo(profile);
            assertThat(sql.queryForList("SELECT * FROM Person.Credential WHERE ProfileId=?",target.getId())).usingRecursiveComparison().isEqualTo(credentials);
            assertThat(users.getMetadata(target.getRowGuid())).noneMatch(item->item.key().equals("forbidden"));
            assertThat(memberships(target)).containsExactly(a,b);
        } finally {sql.update("DELETE Person.ProfileGroup WHERE ProfileId=? AND OrganizationGroupId=?",target.getId(),b);}
    }
    @Test void unassignmentPreservesInactiveForeignMembershipHistory() throws Exception {
        int own=role(orgA,List.of("idp-admin-user:read")),foreign=role(orgB,List.of("idp-admin-user:read"));
        var target=member(orgA,own);
        sql.update("UPDATE Partner.OrganizationGroup SET Status=0 WHERE OrganizationGroupId=?",foreign);
        sql.update("INSERT Person.ProfileOrganization(ProfileId,OrganizationId,Status) VALUES(?,?,0)",target.getId(),orgB.getId());
        sql.update("INSERT Person.ProfileGroup(ProfileId,OrganizationGroupId) VALUES(?,?)",target.getId(),foreign);
        var preserved=sql.queryForList("SELECT * FROM Person.ProfileOrganization WHERE ProfileId=? AND OrganizationId=?",target.getId(),orgB.getId());
        var groupBefore=sql.queryForList("SELECT * FROM Person.ProfileGroup WHERE ProfileId=? AND OrganizationGroupId=?",target.getId(),foreign);
        assertThat(call(platform,"PUT",route(orgA,target),body(null)).statusCode()).isEqualTo(204);
        assertThat(memberships(target)).containsExactly(foreign);
        assertThat(sql.queryForList("SELECT * FROM Person.ProfileOrganization WHERE ProfileId=? AND OrganizationId=?",target.getId(),orgB.getId())).isEqualTo(preserved);
        assertThat(sql.queryForList("SELECT * FROM Person.ProfileGroup WHERE ProfileId=? AND OrganizationGroupId=?",target.getId(),foreign)).isEqualTo(groupBefore);
        sql.update("DELETE Person.ProfileGroup WHERE ProfileId=? AND OrganizationGroupId=?",target.getId(),foreign);
        sql.update("DELETE Person.ProfileOrganization WHERE ProfileId=? AND OrganizationId=?",target.getId(),orgB.getId());
    }
    @Test void concurrentUnassignmentsLeaveOneEffectiveAdministrator() throws Exception {
        var platformOrg=organizations.getById(1).orElseThrow();var one=member(platformOrg,1);var two=member(platformOrg,1);
        var a=token(one);var b=token(two);var disabled=disableOthers(one,two);
        var reached=new CountDownLatch(2);var release=new CountDownLatch(1);
        Mockito.doAnswer(invocation->{reached.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return invocation.callRealMethod();}).when(mutationLock).acquire();
        try(var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->call(a,"PUT",route(platformOrg,one),body(null)).statusCode());
            var second=executor.submit(()->call(b,"PUT",route(platformOrg,two),body(null)).statusCode());
            assertThat(reached.await(10,TimeUnit.SECONDS)).isTrue();release.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(204,409);
            assertThat(sql.queryForObject("SELECT COUNT(DISTINCT ProfileId) FROM Person.EffectivePlatformAdministrator",Integer.class)).isEqualTo(1);
        } finally {release.countDown();Mockito.reset(mutationLock);restore(disabled);}
    }
    @Test void queuedUserWriteDeniesRevokedPermissionAfterAuthentication() throws Exception {
        int role=role(orgA,List.of("idp-admin-user:update"));var actor=token(member(orgA,role));var target=member(orgA,0);
        var reached=new CountDownLatch(1);var release=new CountDownLatch(1);
        Mockito.doAnswer(invocation->{reached.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return invocation.callRealMethod();}).when(mutationLock).acquire();
        try(var connection=Objects.requireNonNull(sql.getDataSource()).getConnection();var statement=connection.createStatement();var executor=Executors.newSingleThreadExecutor()) {
            statement.execute("BEGIN TRANSACTION; DECLARE @r INT; EXEC @r=sys.sp_getapplock @Resource=N'authserver:identity-management:v1',@LockMode='Exclusive',@LockOwner='Transaction';");
            var pending=executor.submit(()->call(actor,"PUT","/api/v1/users/"+target.getRowGuid()+"/metadata","{\"metaData\":{\"forbidden\":\"queued\"}}"));
            try {
                assertThat(reached.await(10,TimeUnit.SECONDS)).isTrue();
                statement.executeUpdate("UPDATE Partner.OrganizationGroupPermission SET Status=0 WHERE OrganizationGroupId="+role);
                statement.execute("COMMIT TRANSACTION");release.countDown();
                assertThat(pending.get(20,TimeUnit.SECONDS).statusCode()).isEqualTo(403);
                assertThat(users.getMetadata(target.getRowGuid())).noneMatch(item->item.key().equals("forbidden"));
            } finally {release.countDown();statement.execute("IF @@TRANCOUNT>0 ROLLBACK TRANSACTION");}
        } finally {release.countDown();Mockito.reset(mutationLock);}
    }

    @Test void queuedAmbiguousActorIsDeniedAfterAuthentication() throws Exception {
        int role=role(orgA,List.of("idp-admin-user:update"));var actorUser=member(orgA,role);var actor=token(actorUser);var target=member(orgA,0);
        var reached=new CountDownLatch(1);var release=new CountDownLatch(1);
        Mockito.doAnswer(invocation->{reached.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return invocation.callRealMethod();}).when(mutationLock).acquire();
        try(var connection=Objects.requireNonNull(sql.getDataSource()).getConnection();var statement=connection.createStatement();var executor=Executors.newSingleThreadExecutor()) {
            statement.execute("BEGIN TRANSACTION; DECLARE @r INT; EXEC @r=sys.sp_getapplock @Resource=N'authserver:identity-management:v1',@LockMode='Exclusive',@LockOwner='Transaction';");
            var pending=executor.submit(()->call(actor,"PUT","/api/v1/users/"+target.getRowGuid()+"/metadata","{\"metaData\":{\"forbidden\":\"queued\"}}"));
            try {
                assertThat(reached.await(10,TimeUnit.SECONDS)).isTrue();
                statement.executeUpdate("INSERT Person.ProfileOrganization(ProfileId,OrganizationId) VALUES("+actorUser.getId()+","+orgA.getId()+")");
                statement.execute("COMMIT TRANSACTION");release.countDown();
                assertThat(pending.get(20,TimeUnit.SECONDS).statusCode()).isEqualTo(403);
                assertThat(users.getMetadata(target.getRowGuid())).noneMatch(item->item.key().equals("forbidden"));
            } finally {release.countDown();statement.execute("IF @@TRANCOUNT>0 ROLLBACK TRANSACTION");}
        } finally {release.countDown();Mockito.reset(mutationLock);sql.update("DELETE FROM Person.ProfileOrganization WHERE ProfileOrganizationId=(SELECT MAX(ProfileOrganizationId) FROM Person.ProfileOrganization WHERE ProfileId=?)",actorUser.getId());}
    }

    @org.springframework.beans.factory.annotation.Autowired
    com.chellavignesh.authserver.adminportal.credential.CredentialService credentials;
    @org.springframework.beans.factory.annotation.Autowired
    com.chellavignesh.authserver.adminportal.resource.ResourceRepository resources;
    @Test void machineCannotDisableOrDeleteFinalPlatformAdministrator() throws Exception {
        var platformOrg=organizations.getById(1).orElseThrow();
        var dto=new com.chellavignesh.authserver.adminportal.application.dto.CreateApplicationDto();
        dto.setName("Guard machine");dto.setDescription("Owned fixture");
        dto.setType(com.chellavignesh.authserver.enums.entity.ApplicationTypeEnum.SERVER);
        dto.setAuthMethod(com.chellavignesh.authserver.enums.entity.AuthFlowEnum.CLIENT_SECRET_JWT);
        dto.setUsernameType(com.chellavignesh.authserver.enums.entity.UsernameTypeEnum.USERNAME);dto.setAllowForgotUsername(false);
        var settings=new com.chellavignesh.authserver.adminportal.application.dto.UpdateTokenSettingsDto();
        settings.setAuthCodeTimeToLive(120);settings.setAccessTokenTimeToLive(600);settings.setRefreshTokenTimeToLive(3600);
        settings.setDeviceCodeTimeToLive(120);settings.setMaxRequestTransitTime(30);settings.setReuseRefreshTokens(false);
        var machine=applications.create(platformOrg.getId(),dto,settings,new com.chellavignesh.authserver.adminportal.forgotusername.ForgotUsernameSetting(List.of()),com.chellavignesh.authserver.enums.entity.AccessTokenFormatEnum.REFERENCE);
        var secret=new com.chellavignesh.authserver.adminportal.credential.dto.CreateCredentialRequestDto();
        secret.setOrgId(platformOrg.getId());secret.setAppId(machine.getId());secret.setName("Owned machine credential");secret.setDescription("Fixture");
        secret.setAlgorithmEnum(com.chellavignesh.authserver.enums.entity.AlgorithmEnum.HS256);
        secret.setAuthFlow(com.chellavignesh.authserver.enums.entity.AuthFlowEnum.CLIENT_SECRET_JWT);
        secret.setCredentialStatus(com.chellavignesh.authserver.adminportal.credential.CredentialStatus.Active);
        secret.setExpireOn(Date.from(java.time.Instant.now().plusSeconds(86400)));credentials.createSharedSecretCredential(secret);
        for(String method:List.of("PUT","DELETE")) {
            var resource=new com.chellavignesh.authserver.adminportal.resource.dto.ResourceLibraryDto();
            resource.setName("Guard user "+method);resource.setDescription("Fixture");resource.setUri("http://user");resource.setAllowedMethod(method);resource.setUrn("urn:guard:"+method);
            var created=resources.create(resource);applications.assignResource(platformOrg.getId(),machine.getId(),created.getId());
        }
        var bearer=token(machine,machine.getClientId(),"confidential-client");var disabled=disableOthers(platformUser);
        try {
            assertThat(call(bearer,"PUT","/api/v1/users/"+platformUser.getRowGuid()+"/status","{\"status\":\"Inactive\"}").statusCode()).isEqualTo(409);
            assertThat(call(bearer,"DELETE","/api/v1/users/"+platformUser.getRowGuid(),null).statusCode()).isEqualTo(409);
            assertThat(sql.queryForObject("SELECT COUNT(DISTINCT ProfileId) FROM Person.EffectivePlatformAdministrator",Integer.class)).isEqualTo(1);
        } finally {restore(disabled);status(platformUser,1);}
    }
    @Test void platformOrganizationCannotBeRevoked() throws Exception {
        var platformOrg=organizations.getById(1).orElseThrow();
        var response=call(platform,"PUT","/api/v1/organizations/"+platformOrg.getRowGuid(),"{\"name\":\"Platform\",\"description\":\"Fixture\",\"status\":\"Revoked\"}");
        assertThat(response.statusCode()).isIn(403,409);
        assertThat(sql.queryForObject("SELECT Status FROM Partner.Organization WHERE OrganizationId=1",Integer.class)).isEqualTo(1);
    }

    @Test void platformCreationCannotExceedGrantCeilingBeforeProviderLookup() throws Exception {
        var platformOrg=organizations.getById(1).orElseThrow();
        int limited=role(platformOrg,List.of("idp-admin-org:switch","idp-admin-user:create","idp-admin-admin:create"));
        var actor=token(member(platformOrg,limited));int high=role(orgA,List.of("idp-admin-app:delete"));
        var target=member(orgA,0);var payload=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(createBody(target,"unknown-provider"));
        UUID newId=UUID.randomUUID();payload.put("memberId",newId.toString());payload.put("groupGuid",guid(high).toString());
        var response=call(actor,"POST","/api/v1/users",mapper.writeValueAsString(payload));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.Profile WHERE RowGuid=?",Integer.class,newId.toString())).isZero();
    }

    @Test void creationRejectsUnprotectedPlatformRoleBeforeProviderLookup() throws Exception {
        assertCreationRejectsPlatformRole(false);
    }

    @Test void creationRejectsProtectedRoleOutsidePlatformOrgBeforeProviderLookup() throws Exception {
        assertCreationRejectsPlatformRole(true);
    }

    void assertCreationRejectsPlatformRole(boolean foreignBuiltin) throws Exception {
        var platformOrg = organizations.getById(1).orElseThrow();
        var organization = foreignBuiltin ? orgA : platformOrg;
        int group = role(organization, List.of("idp-admin-org:switch"));
        if (foreignBuiltin) sql.update("UPDATE Partner.OrganizationGroup SET IsBuiltin=1 WHERE OrganizationGroupId=?", group);
        var pending = new User();
        pending.setRowGuid(UUID.randomUUID());
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(createBody(pending, "unknown-provider"));
        payload.put("orgGuid", organization.getRowGuid().toString());
        payload.put("groupGuid", guid(group).toString());
        var response = call(platform, "POST", "/api/v1/users", mapper.writeValueAsString(payload));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.Profile WHERE RowGuid=?", Integer.class, pending.getRowGuid().toString())).isZero();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.ProfileGroup WHERE OrganizationGroupId=?", Integer.class, group)).isZero();
    }

    @Test void creationAllowsProtectedPlatformRoleThroughAuthorizationOnly() throws Exception {
        var pending = new User();
        pending.setRowGuid(UUID.randomUUID());
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(createBody(pending, "unknown-provider"));
        payload.put("orgGuid", organizations.getById(1).orElseThrow().getRowGuid().toString());
        payload.put("groupGuid", guid(1).toString());
        var response = call(platform, "POST", "/api/v1/users", mapper.writeValueAsString(payload));
        // Missing branding proves the protected builtin passed authorization, not provider success.
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("Invalid Branding");
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.Profile WHERE RowGuid=?", Integer.class, pending.getRowGuid().toString())).isZero();
    }

    @Test void existingLegacyPlatformAdministratorKeepsLifecycleEligibility() throws Exception {
        var platformOrg = organizations.getById(1).orElseThrow();
        int legacy = role(platformOrg, List.of("idp-admin-org:switch"));
        var target = member(platformOrg, legacy);
        var response = call(platform, "PUT", "/api/v1/users/" + target.getRowGuid() + "/status", "{\"status\":\"Inactive\"}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(204);
        assertThat(memberships(target)).containsExactly(legacy);
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(profileBody(target, "unknown-provider"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload.path("urn:ascensus:529:1.0:accountsync"))
                .put("orgId", platformOrg.getRowGuid().toString());
        response = call(platform, "POST", "/api/v1/users/profile", mapper.writeValueAsString(payload));
        // This entrypoint has no role field: reactivation retains the existing role after scope checks.
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("Invalid branding");
        assertThat(memberships(target)).containsExactly(legacy);
        assertThat(sql.queryForObject("SELECT Status FROM Person.Profile WHERE ProfileId=?", Integer.class, target.getId())).isZero();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.Profile WHERE RowGuid=?", Integer.class, target.getRowGuid().toString())).isEqualTo(1);
    }

    @Test void malformedSwitchGrantDoesNotCountAsSurvivingAdministrator() throws Exception {
        var platformOrg=organizations.getById(1).orElseThrow();
        for(String key:List.of("IDP-ADMIN-ORG:SWITCH","idp-admin-org:switch ")) {
            var ineffective=member(platformOrg,role(platformOrg,List.of(key)));var disabled=disableOthers(platformUser,ineffective);
            try {
                var response=call(platform,"PUT","/api/v1/users/"+platformUser.getRowGuid()+"/status","{\"status\":\"Inactive\"}");
                assertThat(response.statusCode()).as("Noncanonical switch grant must not count").isEqualTo(409);
                assertThat(sql.queryForObject("SELECT Status FROM Person.Profile WHERE ProfileId=?",Integer.class,platformUser.getId())).isEqualTo(1);
            } finally {restore(disabled);status(platformUser,1);}
        }
    }

}
