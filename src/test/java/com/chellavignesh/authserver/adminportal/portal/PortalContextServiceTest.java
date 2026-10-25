package com.chellavignesh.authserver.adminportal.portal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.chellavignesh.authserver.adminportal.organization.OrganizationService;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.UserStatus;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.chellavignesh.authserver.security.ActorContext;
import com.chellavignesh.authserver.security.ManagementAccessPolicy;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;

class PortalContextServiceTest {
  final UUID profile = UUID.randomUUID(), login = UUID.randomUUID(), org = UUID.randomUUID();
  final UserService users = mock(UserService.class);
  final ManagementAccessPolicy policy = mock(ManagementAccessPolicy.class);
  final OrganizationService organizations = mock(OrganizationService.class);
  final PortalContextService service =
      new PortalContextService(policy, users, organizations, "unconfigured");

  UserDetails details(UUID user, UUID organization, String username) {
    return new UserDetails(
        1,
        "First",
        "Last",
        username,
        user,
        "email@example.test",
        null,
        null,
        null,
        null,
        false,
        null,
        null,
        user,
        login,
        organization,
        1,
        UserStatus.Active,
        "idp",
        null,
        organization,
        false,
        false);
  }

  BearerTokenAuthentication actor(Map<String, Object> claims) {
    var principal = new OAuth2IntrospectionAuthenticatedPrincipal(claims, List.of());
    var token =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "owned-test-token",
            Instant.now(),
            Instant.now().plusSeconds(30));
    var auth = new BearerTokenAuthentication(principal, token, List.of());
    when(policy.requireHumanActor(auth))
        .thenReturn(new ActorContext(profile, org, false, Set.of()));
    when(users.getByGuid(profile)).thenReturn(Optional.of(details(profile, org, "second")));
    when(users.getByLoginId(login)).thenReturn(Optional.of(details(profile, org, "second")));
    when(users.isManagementIdentityActive(profile, org, login)).thenReturn(true);
    when(organizations.getForMember(profile)).thenReturn(List.of());
    return auth;
  }

  @Test
  void trustedCredentialClaimSelectsPublicIdentity() {
    var auth =
        actor(
            Map.of(
                "active",
                true,
                "sub",
                "second",
                "login_guid",
                login.toString(),
                "metadata",
                Map.of("uuid_login_id", UUID.randomUUID().toString())));
    var result = service.getContext(auth);
    assertThat(result.subject().loginId()).isEqualTo(login);
    assertThat(result.subject().username()).isEqualTo("second");
  }

  @Test
  void mutableNestedIdentityCannotReplaceMissingTrustedClaim() {
    var auth =
        actor(
            Map.of(
                "active",
                true,
                "sub",
                "second",
                "metadata",
                Map.of("uuid_login_id", login.toString())));
    assertThatThrownBy(() -> service.getContext(auth)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void malformedOrForeignTrustedCredentialIsDenied() {
    for (Object invalid : List.of("invalid", UUID.randomUUID())) {
      var auth = actor(Map.of("active", true, "sub", "second", "login_guid", invalid));
      assertThatThrownBy(() -> service.getContext(auth)).isInstanceOf(AccessDeniedException.class);
    }
  }

  @Test
  void trustedCredentialMustMatchVerifiedProfileOrganizationAndSubject() {
    var auth = actor(Map.of("active", true, "sub", "second", "login_guid", login));
    for (var mismatched :
        List.of(
            details(UUID.randomUUID(), org, "second"),
            details(profile, UUID.randomUUID(), "second"),
            details(profile, org, "sibling"))) {
      when(users.getByLoginId(login)).thenReturn(Optional.of(mismatched));
      assertThatThrownBy(() -> service.getContext(auth)).isInstanceOf(AccessDeniedException.class);
    }
  }

  @Test
  void inactiveSqlCredentialCannotLaunchAccountAction() {
    var auth = actor(Map.of("active", true, "sub", "second", "login_guid", login));
    when(users.isManagementIdentityActive(profile, org, login)).thenReturn(false);
    assertThatThrownBy(() -> service.getContext(auth)).isInstanceOf(AccessDeniedException.class);
  }
}
