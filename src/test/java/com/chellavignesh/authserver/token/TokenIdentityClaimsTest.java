package com.chellavignesh.authserver.token;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.organization.OrganizationService;
import com.chellavignesh.authserver.adminportal.organization.OrganizationStatus;
import com.chellavignesh.authserver.adminportal.organization.entity.Organization;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.UserStatus;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.chellavignesh.authserver.enums.entity.ApplicationTypeEnum;
import com.chellavignesh.authserver.enums.entity.AuthSessionStatusEnum;
import com.chellavignesh.authserver.session.AuthSessionService;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.token.entity.Token;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class TokenIdentityClaimsTest {
  final UUID profile = UUID.randomUUID(), login = UUID.randomUUID(), org = UUID.randomUUID();

  Map<String, Object> claims() throws Exception {
    var sessions = mock(AuthSessionService.class);
    var applications = mock(ApplicationService.class);
    var users = mock(UserService.class);
    var organizations = mock(OrganizationService.class);
    var token = new Token();
    token.setSessionId(UUID.randomUUID());
    token.setCreatedOn(Date.from(Instant.now()));
    token.setExpiration(Date.from(Instant.now().plusSeconds(60)));
    var session = new AuthSession();
    session.setApplicationId(1);
    session.setSubjectId("second");
    session.setLoginId(login);
    session.setBranding("idp");
    session.setScopes(Set.of("openid", "metadata", "idp-admin"));
    session.setAuthSessionStatus(AuthSessionStatusEnum.ACTIVE);
    when(sessions.getBySessionId(token.getSessionId())).thenReturn(Optional.of(session));
    var app = new Application();
    app.setOrgId(1);
    app.setActive(true);
    app.setType(ApplicationTypeEnum.WEB);
    when(applications.getById(1)).thenReturn(Optional.of(app));
    when(applications.getRegisteredClientById(1))
        .thenReturn(
            RegisteredClient.withId("client")
                .clientId("client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/callback")
                .build());
    var organization = new Organization();
    organization.setRowGuid(org);
    organization.setStatus(OrganizationStatus.ACTIVE);
    when(organizations.getById(1)).thenReturn(Optional.of(organization));
    var user =
        new UserDetails(
            1,
            "First",
            "Last",
            "second",
            profile,
            "email@example.test",
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            profile,
            login,
            org,
            1,
            UserStatus.Active,
            "idp",
            null,
            org,
            false,
            false);
    when(users.getByLoginId(login)).thenReturn(Optional.of(user));
    when(users.isManagementIdentityActive(profile, org, login)).thenReturn(true);
    when(users.getMetadata(profile))
        .thenReturn(
            Map.of(
                "uuid_member_id",
                UUID.randomUUID().toString(),
                "uuid_login_id",
                UUID.randomUUID().toString(),
                "login_guid",
                UUID.randomUUID().toString()));
    when(users.getUserPermissions(1)).thenReturn(Map.of("login_guid", List.of("custom-value")));
    return new TokenService(
            mock(TokenRepository.class), sessions, applications, users, organizations, false)
        .getClaimsForToken(token);
  }

  @Test
  void customClaimsCannotOverwriteTrustedTopLevelCredential() throws Exception {
    assertThat(claims().get("login_guid")).isEqualTo(login);
  }

  @Test
  void customMetadataCannotOverwriteReservedNestedIdentity() throws Exception {
    var metadata = (Map<?, ?>) claims().get("metadata");
    assertThat(metadata.get("uuid_member_id")).isEqualTo(profile);
    assertThat(metadata.get("uuid_login_id")).isEqualTo(login);
  }
}
