package com.chellavignesh.authserver.security;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.organization.*;
import com.chellavignesh.authserver.adminportal.organization.entity.*;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.UserStatus;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.chellavignesh.authserver.enums.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagementAccessPolicyTest {
    final UUID orgA=UUID.randomUUID(), orgB=UUID.randomUUID(), actorId=UUID.randomUUID(), targetId=UUID.randomUUID(), groupId=UUID.randomUUID();
    final UserService users=mock(UserService.class);
    final OrganizationService organizations=mock(OrganizationService.class);
    final ApplicationService applications=mock(ApplicationService.class);
    final ManagementAccessPolicy policy=new ManagementAccessPolicy(users,organizations,applications);
    @BeforeEach void fixtures() {
        when(users.getByGuid(actorId)).thenReturn(Optional.of(user(actorId,orgA,groupId)));
        when(users.getByGuid(targetId)).thenReturn(Optional.of(user(targetId,orgA,null)));
        when(organizations.get(orgA)).thenReturn(Optional.of(new Organization(1,orgA,"A",null,OrganizationStatus.ACTIVE,null,null)));
        when(organizations.get(orgB)).thenReturn(Optional.of(new Organization(2,orgB,"B",null,OrganizationStatus.ACTIVE,null,null)));
        var own = organizations.get(orgA).orElseThrow();
        when(organizations.getForMember(actorId)).thenReturn(List.of(own));
    }
    @Test void tenantReadWithoutFilterIsScoped() {
        var actor=bearer(Map.of("idp-admin-user",new String[]{"read"}));
        assertThat(policy.requireOrganizationScope(actor,null)).isEqualTo(orgA);
        assertThatThrownBy(()->policy.requireOrganizationScope(actor,orgB)).isInstanceOf(AccessDeniedException.class);
        assertThat(policy.requireUserListType(actor,Optional.empty())).contains("User");
    }
    @Test void foreignUserMetadataIsDenied() {
        when(users.getByGuid(targetId)).thenReturn(Optional.of(user(targetId,orgB,null)));
        var permissions=new PermissionsService(policy);
        assertThat(permissions.isSubjectNonAdminUser(bearer(Map.of("idp-admin-user",new String[]{"read"})),targetId)).isFalse();
    }
    @Test void orgAdminCanManageEligibleLocalUser() {
        assertThatCode(()->policy.requireUserMutation(bearer(Map.of("idp-admin-user",new String[]{"update"})),targetId,"update")).doesNotThrowAnyException();
        assertThat(policy.canCreateUser(bearer(Map.of("idp-admin-user",new String[]{"create"})),orgA,null)).isTrue();
    }
    @Test void developerCannotManageUsers() {
        assertThat(policy.canMutateUser(bearer(Map.of("idp-admin-app",new String[]{"update"})),targetId,"update")).isFalse();
    }
    @Test void forgedGroupDoesNotGrantAdmin() {
        var actor=bearer(Map.of("group_guid",UUID.randomUUID()));
        assertThat(policy.canMutateUser(actor,targetId,"update")).isFalse();
        assertThat(policy.canCreateUser(actor,orgA,groupId)).isFalse();
        assertThat(policy.isPlatformAdmin(actor)).isFalse();
    }
    @Test void humanCannotUseMachineUrlPermissionAsFallback() {
        assertThat(policy.canMutateUser(bearer(Map.of("url-permissions",new String[]{"http://user PUT"})),targetId,"update")).isFalse();
    }
    @Test void machineCannotTargetForeignOrg() {
        var actor=machine();
        assertThat(policy.canReadUser(actor,targetId)).isTrue();
        when(users.getByGuid(targetId)).thenReturn(Optional.of(user(targetId,orgB,null)));
        assertThat(policy.canReadUser(actor,targetId)).isFalse();
        assertThat(policy.canMutateUser(actor,targetId,"update")).isFalse();
        assertThat(policy.canCreateUser(actor,orgB,null)).isFalse();
    }
    @Test void machineCannotForgeItsRegisteredOrganization() {
        var actor=machine();
        when(applications.getByClientId("machine").orElseThrow().getOrgId()).thenReturn(2);
        var foreign=organizations.get(orgB);when(organizations.getById(2)).thenReturn(foreign);
        assertThat(policy.canReadUser(actor,targetId)).isFalse();
    }
    @Test void cookieOnlyManagementRequestIsUnauthorized() {
        var session=UsernamePasswordAuthenticationToken.authenticated("fixture",null,List.of());
        assertThatThrownBy(()->policy.requireHumanActor(session)).isInstanceOf(AccessDeniedException.class);
        assertThat(policy.canReadUser(session,targetId)).isFalse();
        assertThat(policy.canListUsers(null)).isFalse();
    }
    @Test void deactivatedOrganizationIsDenied() {
        var organization=organizations.get(orgA).orElseThrow();organization.setStatus(OrganizationStatus.INACTIVE);
        assertThat(policy.canListUsers(bearer(Map.of("idp-admin-user",new String[]{"read"})))).isFalse();
    }
    @Test void platformSelectedTenantDoesNotChangeActorIdentity() {
        var token=bearer(Map.of("idp-admin-org",List.of("switch")));
        assertThat(policy.requireOrganizationScope(token,orgB)).isEqualTo(orgB);
        assertThat(policy.requireHumanActor(token).organizationId()).isEqualTo(orgA);
    }
    @Test void adminTargetRequiresBothExplicitActionsAndGrantCeiling() {
        when(users.getByGuid(targetId)).thenReturn(Optional.of(user(targetId,orgA,groupId)));
        when(organizations.getOrganizationGroup(1,groupId)).thenReturn(Optional.of(new OrganizationGroup(8,groupId,"Admin",null,OrganizationStatus.ACTIVE,1)));
        OrganizationGroupPermission grant=new OrganizationGroupPermission();grant.setKey("idp-admin-app:update");grant.setStatus(OrganizationStatus.ACTIVE);
        when(organizations.getOrganizationGroupPermissions(1,8)).thenReturn(List.of(grant));
        assertThat(policy.canMutateUser(bearer(Map.of("idp-admin-user",List.of("update"))),targetId,"update")).isFalse();
        assertThat(policy.canMutateUser(bearer(Map.of("idp-admin-admin",List.of("update"))),targetId,"update")).isFalse();
        assertThat(policy.canMutateUser(bearer(Map.of("idp-admin-user",List.of("update"),"idp-admin-admin",List.of("update"))),targetId,"update")).isFalse();
        assertThat(policy.canMutateUser(bearer(Map.of("idp-admin-user",List.of("update"),"idp-admin-admin",List.of("update"),"idp-admin-app",List.of("update"))),targetId,"update")).isTrue();
    }
    @Test void tenantCannotCreateRoleWithReservedGrants() {
        when(organizations.getOrganizationGroup(1,groupId)).thenReturn(Optional.of(new OrganizationGroup(8,groupId,"Reserved",null,OrganizationStatus.ACTIVE,1)));
        var grant=new OrganizationGroupPermission();grant.setKey("idp-admin-org:switch");grant.setStatus(OrganizationStatus.ACTIVE);
        when(organizations.getOrganizationGroupPermissions(1,8)).thenReturn(List.of(grant));
        assertThat(policy.canCreateUser(bearer(Map.of("idp-admin-user",List.of("create"),"idp-admin-admin",List.of("create"))),orgA,groupId)).isFalse();
    }
    @Test void revokedMembershipDeniesTenantAndPlatformActors() {
        when(organizations.getForMember(actorId)).thenReturn(List.of());
        for(var claims:List.<Map<String,Object>>of(Map.of("idp-admin-user",List.of("read","update")),
                Map.of("idp-admin-org",List.of("switch"),"idp-admin-user",List.of("read","update")))) {
            var actor=bearer(claims);
            assertThatThrownBy(()->policy.requireHumanActor(actor)).isInstanceOf(AccessDeniedException.class);
            assertThat(policy.canListUsers(actor)).isFalse();
            assertThat(policy.canMutateUser(actor,targetId,"update")).isFalse();
        }
    }
    BearerTokenAuthentication machine() {
        var app=mock(Application.class);when(app.getOrgId()).thenReturn(1);when(app.getActive()).thenReturn(true);when(app.getType()).thenReturn(ApplicationTypeEnum.SERVER);when(app.getAuthFlow()).thenReturn(AuthFlowEnum.CLIENT_SECRET_JWT);
        when(applications.getByClientId("machine")).thenReturn(Optional.of(app));var own=organizations.get(orgA);when(organizations.getById(1)).thenReturn(own);
        return token(Map.of("active",true,"org_guid",orgA,"client_id","machine","url-permissions",new String[]{"http://user GET","http://user PUT","http://user POST"}));
    }
    BearerTokenAuthentication bearer(Map<String,Object> extras) {
        var claims=new HashMap<String,Object>(extras);claims.put("active",true);claims.put("org_guid",orgA);claims.put("rowguid",actorId);claims.put("sub","fixture");
        return token(claims);
    }
    BearerTokenAuthentication token(Map<String,Object> claims) {
        return new BearerTokenAuthentication(new OAuth2IntrospectionAuthenticatedPrincipal(claims,List.of()),new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"fixture",Instant.now(),Instant.now().plusSeconds(600)),List.of());
    }
    UserDetails user(UUID id,UUID org,UUID group) {
        return new UserDetails(7,"Test","User","fixture",id,"test@example.test",null,null,null,"+19999999999",false,group,null,id,UUID.randomUUID(),org,1,UserStatus.Active,"idp",null,org,false,false);
    }
}
