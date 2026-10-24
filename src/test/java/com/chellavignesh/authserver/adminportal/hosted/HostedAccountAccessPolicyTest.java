package com.chellavignesh.authserver.adminportal.hosted;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.externalsource.ExternalSourceRepository;
import com.chellavignesh.authserver.adminportal.externalsource.entity.*;
import com.chellavignesh.authserver.adminportal.user.*;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.chellavignesh.authserver.enums.entity.*;
import com.chellavignesh.authserver.security.RequestDatetimeValidator;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.session.fingerprint.ClientFingerprint;
import com.chellavignesh.authserver.session.sso.SingleSignOnService;
import com.chellavignesh.authserver.token.TokenService;
import com.chellavignesh.authserver.token.entity.Token;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.*;

class HostedAccountAccessPolicyTest {
    final ApplicationService apps = mock(ApplicationService.class);
    final SingleSignOnService sso = mock(SingleSignOnService.class);
    final UserService users = mock(UserService.class);
    final ExternalSourceRepository sources = mock(ExternalSourceRepository.class);
    final RequestDatetimeValidator dates = mock(RequestDatetimeValidator.class);
    final TokenService tokens = mock(TokenService.class);
    final UUID login = UUID.randomUUID(), profile = UUID.randomUUID(), org = UUID.randomUUID();
    final String date = "2026-10-24T10:00:00Z";
    final URI callback = URI.create("http://127.0.0.1:3100/auth/callback"),
            returnTo = URI.create("http://127.0.0.1:3100/account");
    HostedAccountAccessPolicy policy;
    AuthSession session;
    Application app;
    UserDetails user;

    @BeforeEach
    void setup() throws Exception {
        app = new Application();
        app.setId(7);
        app.setClientId("portal");
        app.setActive(true);
        app.setType(ApplicationTypeEnum.WEB);
        when(apps.getByClientId("portal")).thenReturn(Optional.of(app));
        when(apps.getRegisteredClientById(7))
                .thenReturn(
                        RegisteredClient.withId("portal")
                                .clientId("portal")
                                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                                .redirectUri(callback.toString())
                                .build());
        session = new AuthSession();
        session.setAuthSessionId(1);
        session.setSessionId(UUID.randomUUID());
        session.setApplicationId(7);
        session.setSubjectId("owned");
        session.setBranding("idp");
        session.setAuthSessionStatus(AuthSessionStatusEnum.ACTIVE);
        session.setRedirectUri(callback.toString());
        session.setClientFingerprint(
                new ClientFingerprint(ZoneOffset.UTC, "en-US", "Browser", "127.0.0.1").getBytes());
        when(sso.getAuthSessionFromCookie(any())).thenReturn(Optional.of(session));
        user =
                new UserDetails(
                        1,
                        "Owned",
                        "Person",
                        "owned",
                        profile,
                        "owned@example.test",
                        null,
                        null,
                        null,
                        "+19999999998",
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
        when(users.getByUsernameAndBranding("owned", "idp")).thenReturn(Optional.of(user));
        when(users.isManagementIdentityActive(profile, org, login)).thenReturn(true);
        when(sources.findAllByBrandingSourceCode(List.of("idp")))
                .thenReturn(
                        List.of(
                                new ExternalSource(
                                        UUID.randomUUID(),
                                        "idp",
                                        false,
                                        new ExternalType(1, "IDP", null, null))));
        when(dates.validateRequestDatetime(date, 7)).thenReturn(true);
        Token token = new Token();
        token.setTokenType(TokenTypeEnum.ACCESS_TOKEN);
        when(tokens.getAllActiveBySessionId(session.getSessionId())).thenReturn(List.of(token));
        when(tokens.isTokenActive(token, session)).thenReturn(true);
        policy =
                new HostedAccountAccessPolicy(
                        apps,
                        sso,
                        users,
                        sources,
                        AuthorizationServerSettings.builder()
                                .issuer("http://localhost:8080/services")
                                .build(),
                        dates,
                        tokens);
    }

    MockHttpServletRequest request() {
        var r = new MockHttpServletRequest("GET", "/portal/account/profile");
        r.setServletPath("/portal/account/profile");
        r.addHeader("Referer", "http://localhost:8080/services/portal/account-action");
        r.addHeader("User-Agent", "Browser");
        r.addHeader("Accept-Language", "en-US");
        r.setCookies(
                new Cookie(
                        "ags-idp-session",
                        Base64.getEncoder().encodeToString(new byte[] {1, 2, 3})));
        return r;
    }

    HostedAccountAccessPolicy.ValidatedLaunch launch() {
        return policy.validateLaunch("profile", "portal", login, returnTo);
    }

    @Test
    void allowsLocalAccountWithoutAdminGrants() {
        var actor = policy.requireActor(request(), launch(), date);
        assertThat(actor.loginId()).isEqualTo(login);
        assertThat(actor.profileId()).isEqualTo(profile);
        assertThat(user.groupId()).isNull();
    }

    @Test
    void rejectsPortalIssuerSubjectMismatch() {
        var other = policy.validateLaunch("profile", "portal", UUID.randomUUID(), returnTo);
        assertThatThrownBy(() -> policy.requireActor(request(), other, date))
                .isInstanceOf(HostedAccountFailure.class);
    }

    @Test
    void rejectsForeignOrMissingIssuerReferer() {
        for (String v :
                List.of(
                        "https://evil.test/",
                        "http://localhost:9999/",
                        "http://user@localhost:8080/",
                        "not-a-uri")) {
            var r = request();
            r.removeHeader("Referer");
            r.addHeader("Referer", v);
            r.addHeader("Host", "localhost:8080");
            r.addHeader("Forwarded", "host=localhost:8080");
            assertThatThrownBy(() -> policy.requireActor(r, launch(), date))
                    .isInstanceOf(HostedAccountFailure.class);
        }
        var absent = request();
        absent.removeHeader("Referer");
        assertThatThrownBy(() -> policy.requireActor(absent, launch(), date))
                .isInstanceOf(HostedAccountFailure.class);
    }

    @Test
    void preservesUtcFingerprintAndRejectsAlteredOffset() throws Exception {
        assertThat(policy.requireActor(request(), launch(), date)).isNotNull();
        when(dates.validateRequestDatetime(anyString(), eq(7))).thenReturn(true);
        assertThatThrownBy(
                        () -> policy.requireActor(request(), launch(), "2026-10-24T15:30:00+05:30"))
                .isInstanceOf(HostedAccountFailure.class);
        for (String h : List.of("User-Agent", "Accept-Language")) {
            var r = request();
            r.removeHeader(h);
            r.addHeader(h, "changed");
            assertThatThrownBy(() -> policy.requireActor(r, launch(), date))
                    .isInstanceOf(HostedAccountFailure.class);
        }
    }

    @Test
    void requiresActiveLocalHumanAndRegisteredWebCallback() throws Exception {
        session.setAuthSessionStatus(AuthSessionStatusEnum.INACTIVE);
        assertThatThrownBy(() -> policy.requireActor(request(), launch(), date))
                .isInstanceOf(HostedAccountFailure.class);
        session.setAuthSessionStatus(AuthSessionStatusEnum.ACTIVE);
        when(tokens.getAllActiveBySessionId(session.getSessionId())).thenReturn(List.of());
        assertThatThrownBy(() -> policy.requireActor(request(), launch(), date))
                .isInstanceOf(HostedAccountFailure.class);
        app.setType(ApplicationTypeEnum.SERVER);
        assertThatThrownBy(this::launch).isInstanceOf(HostedAccountFailure.class);
        app.setType(ApplicationTypeEnum.WEB);
        when(sources.findAllByBrandingSourceCode(anyList()))
                .thenReturn(
                        List.of(
                                new ExternalSource(
                                        UUID.randomUUID(),
                                        "idp",
                                        true,
                                        new ExternalType(2, "SYNC", null, null))));
        assertThatThrownBy(() -> policy.requireActor(request(), launch(), date))
                .isInstanceOf(HostedAccountFailure.class);
    }

    @Test
    void missingSsoAndOrdinaryRoutesCannotUseHostedException() {
        var missing = request();
        missing.setCookies();
        assertThatThrownBy(() -> policy.requireActor(missing, launch(), date))
                .isInstanceOf(HostedAccountFailure.class);
        for (String path :
                List.of(
                        "/api/v1/users",
                        "/oauth2/sso",
                        "/user/change-profile",
                        "/portal/account/profile/extra")) {
            var r = request();
            r.setServletPath(path);
            assertThatThrownBy(() -> policy.requireActor(r, launch(), date))
                    .isInstanceOf(HostedAccountFailure.class);
        }
    }

    @Test
    void launchRejectsForeignUnregisteredOrMalformedTargets() {
        for (String target :
                List.of(
                        "https://evil.test/account",
                        "http://127.0.0.1:3100/admin",
                        "http://127.0.0.1:3100/account?x=1",
                        "http://127.0.0.1:3100/account#x",
                        "http://user@127.0.0.1:3100/account"))
            assertThatThrownBy(
                            () ->
                                    policy.validateLaunch(
                                            "profile", "portal", login, URI.create(target)))
                    .isInstanceOf(HostedAccountFailure.class);
        assertThatThrownBy(() -> policy.validateLaunch("delete", "portal", login, returnTo))
                .isInstanceOf(HostedAccountFailure.class);
    }

    @Test
    void invalidCookieIsDeniedWithoutDependencyOrCookieDetails() throws Exception {
        when(sso.getAuthSessionFromCookie(any()))
                .thenThrow(
                        new com.chellavignesh.authserver.session.sso.exception
                                .InvalidSingleSignOnCookieException());
        var failure =
                catchThrowableOfType(
                        HostedAccountFailure.class,
                        () -> policy.requireActor(request(), launch(), date));
        assertThat(failure.status()).isEqualTo(403);
    }

    @Test
    void eitherSyncFlagOrUnknownLocalModeFailsClosed() {
        for (Boolean credential : Arrays.asList(Boolean.TRUE, null)) {
            var synchronizedUser =
                    new UserDetails(
                            user.id(),
                            user.firstName(),
                            user.lastName(),
                            user.username(),
                            user.rowGuid(),
                            user.email(),
                            null,
                            null,
                            null,
                            user.phoneNumber(),
                            false,
                            null,
                            null,
                            user.memberId(),
                            user.loginId(),
                            user.orgId(),
                            1,
                            user.status(),
                            user.branding(),
                            null,
                            user.profileOrgId(),
                            credential,
                            false);
            when(users.getByUsernameAndBranding("owned", "idp"))
                    .thenReturn(Optional.of(synchronizedUser));
            var failure =
                    catchThrowableOfType(
                            HostedAccountFailure.class,
                            () -> policy.requireActor(request(), launch(), date));
            assertThat(failure.status()).isEqualTo(403);
            assertThat(failure.getMessage()).contains("external provider");
        }
    }

    @Test
    void localIdpAccountUsesCredentialAndProfileSyncFlagsNotSourceDefault() {
        when(sources.findAllByBrandingSourceCode(List.of("idp")))
                .thenReturn(
                        List.of(
                                new ExternalSource(
                                        UUID.randomUUID(),
                                        "idp",
                                        true,
                                        new ExternalType(1, "IDP", null, null))));
        assertThatCode(() -> policy.requireActor(request(), launch(), date))
                .doesNotThrowAnyException();
    }
}
