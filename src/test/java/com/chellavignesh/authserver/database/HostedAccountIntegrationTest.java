package com.chellavignesh.authserver.database;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.config.*;
import com.chellavignesh.authserver.config.JdbcOAuth2AuthorizationService;
import com.chellavignesh.authserver.session.*;
import com.chellavignesh.authserver.session.dto.CustomUserDetails;
import com.chellavignesh.authserver.support.ManagementHttpTestSupport;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.savedrequest.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@EnabledIfEnvironmentVariable(named = "AUTH_APP_IT", matches = "true")
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.local.bootstrap.enabled=false",
            "toggles.signature.required=true",
            "logging.level.root=WARN",
            "logback.access.enabled=false"
        })
class HostedAccountIntegrationTest extends ManagementHttpTestSupport {
    static final OwnedDatabase database = new OwnedDatabase();

    @DynamicPropertySource
    static void isolate(DynamicPropertyRegistry registry) {
        database.register(registry);
    }

    @Autowired CustomAuthenticationSuccessHandler successHandler;
    @Autowired CustomUserDetailsService userDetails;
    @Autowired PasswordEncoderFactory passwordFactory;
    @Autowired com.chellavignesh.authserver.token.TokenService tokenService;

    @MockitoSpyBean
    com.chellavignesh.authserver.adminportal.hosted.HostedAccountRevocationRepository revocations;

    @Autowired com.chellavignesh.authserver.adminportal.user.UserService userService;
    @Autowired JdbcOAuth2AuthorizationService grants;
    @Autowired NamedParameterJdbcTemplate named;
    @Autowired RegisteredClientRepository clients;
    @Autowired CredentialAuthenticationLock credentialLock;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockitoSpyBean ApplicationService applicationService;
    @Autowired com.chellavignesh.authserver.session.sso.SingleSignOnService sso;

    @Autowired
    org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
            issuerSettings;

    @BeforeAll
    void fixture() throws Exception {
        orgA = organization("Hosted account integration");
        app = application(orgA);
        redirects.createRedirectUris(
                orgA.getId(), app.getId(), List.of("http://127.0.0.1:3100/auth/callback"));
    }

    @AfterAll
    void cleanup() {
        cleanup(database);
    }

    @AfterEach
    void resetSpies() {
        reset(applicationService, revocations);
        SecurityContextHolder.clearContext();
    }

    @Test
    void queuedOldPasswordSnapshotCannotCreateAnInteractiveSession() throws Exception {
        String name = "queued-human-" + UUID.randomUUID().toString().substring(0, 8);
        var target = user(orgA, 0, name);
        sql.update(
                "UPDATE Person.Credential SET Version=? WHERE ProfileId=?",
                PasswordEncoderFactory.currentVersion,
                target.getId());
        sql.update(
                "UPDATE Person.Profile SET TwoFactorEnabled=0 WHERE ProfileId=?", target.getId());
        var captured =
                (CustomUserDetails)
                        userDetails.loadUserByUsername(name + System.lineSeparator() + "idp");
        var authorize = new MockHttpServletRequest("GET", "/services/oauth2/authorize");
        authorize.addParameter("client_id", app.getClientId());
        authorize.addParameter("scope", "openid profile");
        var request = new MockHttpServletRequest("POST", "/services/login");
        var browser = request.getSession();
        browser.setAttribute(
                "SPRING_SECURITY_SAVED_REQUEST",
                new DefaultSavedRequest(
                        authorize, new org.springframework.security.web.PortResolverImpl()));
        browser.setAttribute(ApplicationConstants.BRANDING_INFO, "idp");
        browser.setAttribute(ApplicationConstants.CLIENT_FINGERPRINT, new byte[] {1, 2, 3});
        var response = new MockHttpServletResponse();
        CountDownLatch verified = new CountDownLatch(1), commitPassword = new CountDownLatch(1);
        try (var thread = Executors.newSingleThreadExecutor()) {
            var queued =
                    thread.submit(
                            () -> {
                                verified.countDown();
                                try {
                                    commitPassword.await();
                                    successHandler.onAuthenticationSuccess(
                                            request,
                                            response,
                                            UsernamePasswordAuthenticationToken.authenticated(
                                                    captured, null, List.of()));
                                } catch (Exception e) {
                                    throw new CompletionException(e);
                                }
                            });
            assertThat(verified.await(5, TimeUnit.SECONDS)).isTrue();
            sql.update(
                    "UPDATE Person.Credential SET Password=0xAABBCCDD WHERE ProfileId=?",
                    target.getId());
            commitPassword.countDown();
            queued.get(15, TimeUnit.SECONDS);
        } finally {
            commitPassword.countDown();
        }
        assertThat(
                        sql.queryForObject(
                                "SELECT COUNT(*) FROM Token.AuthSession WHERE SubjectId=? AND"
                                        + " Branding='idp'",
                                Integer.class,
                                name))
                .isZero();
        assertThat(response.getStatus()).isIn(401, 403);
    }

    @Test
    void queuedRevokedHumanGrantCannotBeRecreatedAsInitialState() throws Exception {
        String name = "queued-grant-" + UUID.randomUUID().toString().substring(0, 8);
        var target = user(orgA, 0, name);
        var access = token(target);
        String session =
                sql.queryForObject(
                        "SELECT CONVERT(VARCHAR(36),SessionId) FROM Token.Token WHERE Data=?",
                        String.class,
                        access.value());
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        var client = applicationService.getRegisteredClientById(app.getId());
        var original =
                OAuth2Authorization.withRegisteredClient(client)
                        .id(id)
                        .principalName(name)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .attribute(
                                JdbcOAuth2AuthorizationService.class.getName() + ".sessionId",
                                session)
                        .authorizedScopes(Set.of("openid"))
                        .accessToken(
                                new OAuth2AccessToken(
                                        OAuth2AccessToken.TokenType.BEARER,
                                        "prior-" + UUID.randomUUID(),
                                        now,
                                        now.plusSeconds(300),
                                        Set.of("openid")))
                        .build();
        var persisted =
                new org.springframework.security.oauth2.server.authorization
                        .JdbcOAuth2AuthorizationService(named.getJdbcTemplate(), clients);
        persisted.save(original);
        var captured = grants.findById(id);
        assertThat(captured != null).isTrue();
        CountDownLatch queuedAtApplication = new CountDownLatch(1),
                commitRevocation = new CountDownLatch(1);
        AtomicBoolean queuedThread = new AtomicBoolean(false);
        doAnswer(
                        invocation -> {
                            if (queuedThread.get()
                                    && Thread.currentThread()
                                            .getName()
                                            .startsWith("hosted-queued")) {
                                queuedAtApplication.countDown();
                                if (!commitRevocation.await(10, TimeUnit.SECONDS))
                                    throw new IllegalStateException("Test barrier expired");
                            }
                            return invocation.callRealMethod();
                        })
                .when(applicationService)
                .getByClientId(app.getClientId());
        AtomicBoolean rejected = new AtomicBoolean();
        try (var executor =
                Executors.newSingleThreadExecutor(r -> new Thread(r, "hosted-queued-grant"))) {
            queuedThread.set(true);
            var queued =
                    executor.submit(
                            () -> {
                                try {
                                    grants.save(
                                            OAuth2Authorization.from(captured)
                                                    .accessToken(
                                                            new OAuth2AccessToken(
                                                                    OAuth2AccessToken.TokenType
                                                                            .BEARER,
                                                                    "new-" + UUID.randomUUID(),
                                                                    now,
                                                                    now.plusSeconds(300),
                                                                    Set.of("openid")))
                                                    .build());
                                } catch (RuntimeException denial) {
                                    rejected.set(true);
                                }
                            });
            assertThat(queuedAtApplication.await(5, TimeUnit.SECONDS)).isTrue();
            sql.update(
                    "UPDATE Token.AuthSession SET AuthSessionStatusId=? WHERE SessionId=?",
                    com.chellavignesh.authserver.enums.entity.AuthSessionStatusEnum.INACTIVE
                            .getValue(),
                    session);
            sql.update("DELETE dbo.oauth2_authorization WHERE id=?", id);
            commitRevocation.countDown();
            queued.get(15, TimeUnit.SECONDS);
        } finally {
            commitRevocation.countDown();
        }
        assertThat(rejected.get()).isTrue();
        assertThat(
                        sql.queryForObject(
                                "SELECT COUNT(*) FROM dbo.oauth2_authorization WHERE id=?",
                                Integer.class,
                                id))
                .isZero();
    }

    final class HostedBrowser {
        final Map<String, String> cookies = new LinkedHashMap<>();
        String userAgent = "PolicyIntegration/1.0", language = "en-US", refererOverride;

        java.net.http.HttpResponse<String> send(
                String method, String path, Map<String, String> form, String origin)
                throws Exception {
            String issuerOrigin =
                    java.net.URI.create(issuerSettings.getIssuer()).getScheme()
                            + "://"
                            + java.net.URI.create(issuerSettings.getIssuer()).getRawAuthority();
            var request =
                    java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create(
                                            "http://localhost:" + port + "/services" + path))
                            .timeout(Duration.ofSeconds(20))
                            .header("User-Agent", userAgent)
                            .header("Accept-Language", language);
            if (!"omit".equals(refererOverride))
                request.header(
                        "Referer", refererOverride == null ? issuerOrigin + "/" : refererOverride);
            if (origin != null) request.header("Origin", origin);
            if (!cookies.isEmpty())
                request.header(
                        "Cookie",
                        cookies.entrySet().stream()
                                .map(e -> e.getKey() + "=" + e.getValue())
                                .collect(java.util.stream.Collectors.joining("; ")));
            if (form != null) request.header("Content-Type", "application/x-www-form-urlencoded");
            String body = form == null ? "" : encode(form);
            var response =
                    http.send(
                            request.method(
                                            method,
                                            form == null
                                                    ? java.net.http.HttpRequest.BodyPublishers
                                                            .noBody()
                                                    : java.net.http.HttpRequest.BodyPublishers
                                                            .ofString(body))
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            for (String value : response.headers().allValues("set-cookie"))
                for (var cookie : java.net.HttpCookie.parse(value))
                    if (cookie.getMaxAge() == 0) cookies.remove(cookie.getName());
                    else cookies.put(cookie.getName(), cookie.getValue());
            return response;
        }
    }

    record HostedFixture(
            com.chellavignesh.authserver.adminportal.user.entity.User user,
            HostedBrowser browser,
            String launch,
            Fixture access,
            String password) {}

    HostedFixture hosted(String name) throws Exception {
        return hosted(name, orgA);
    }

    HostedFixture hosted(
            String name,
            com.chellavignesh.authserver.adminportal.organization.entity.Organization owner)
            throws Exception {
        String password = "S8!qV" + UUID.randomUUID().toString().replace("-", "") + "mT";
        var dto = new com.chellavignesh.authserver.adminportal.user.dto.CreateUserDto();
        dto.setOrgId(owner.getId());
        dto.setFirstName("Policy");
        dto.setLastName("Owned");
        dto.setUsername(name);
        dto.setEmail(name + "@example.test");
        dto.setPhoneNumber("+19999999998");
        dto.setSyncFlag(false);
        dto.setMetaData(Map.of());
        var user =
                users.create(
                        dto,
                        passwordFactory.encode(
                                Base64.getEncoder()
                                        .encodeToString(
                                                password.getBytes(
                                                        java.nio.charset.StandardCharsets.UTF_8))),
                        PasswordEncoderFactory.currentVersion,
                        0,
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        12);
        var token = token(user);
        String id =
                sql.queryForObject(
                        "SELECT CONVERT(VARCHAR(36),SessionId) FROM Token.Token WHERE Data=?",
                        String.class,
                        token.value());
        byte[] fingerprint =
                new com.chellavignesh.authserver.session.fingerprint.ClientFingerprint(
                                ZoneOffset.UTC, "en-US", "PolicyIntegration/1.0", "127.0.0.1")
                        .getBytes();
        int redirect =
                sql.queryForObject(
                        "SELECT RedirectUriId FROM Client.RedirectUri WHERE ApplicationId=?",
                        Integer.class,
                        app.getId());
        sql.update(
                "UPDATE Token.AuthSession SET ClientFingerprint=?,RedirectUriId=? WHERE"
                        + " SessionId=?",
                fingerprint,
                redirect,
                id);
        var browser = new HostedBrowser();
        browser.cookies.put(
                "ags-idp-session",
                Base64.getEncoder()
                        .encodeToString(
                                sso.generateCookie(UUID.fromString(id)).encryptedSessionId()));
        var details = userService.getByGuid(user.getRowGuid()).orElseThrow();
        var parameters = new LinkedHashMap<String, String>();
        parameters.put("action", "profile");
        parameters.put("clientId", app.getClientId());
        parameters.put("expectedLoginId", details.loginId().toString());
        parameters.put("returnTo", "http://127.0.0.1:3100/account");
        parameters.put("x-request-datetime", Instant.now().toString());
        return new HostedFixture(
                user, browser, "/portal/account-action?" + encode(parameters), token, password);
    }

    static String encode(Map<String, String> values) {
        return values.entrySet().stream()
                .map(
                        e ->
                                java.net.URLEncoder.encode(
                                                e.getKey(), java.nio.charset.StandardCharsets.UTF_8)
                                        + "="
                                        + java.net.URLEncoder.encode(
                                                e.getValue(),
                                                java.nio.charset.StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    static String unescape(String value) {
        return value.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"");
    }

    static String continuePath(String html) {
        var match =
                java.util.regex.Pattern.compile("href=\"([^\"]+)\"[^>]*>Continue</a>")
                        .matcher(html);
        assertThat(match.find()).isTrue();
        var target = java.net.URI.create(unescape(match.group(1)));
        return target.getRawPath().replaceFirst("^/services", "") + "?" + target.getRawQuery();
    }

    static Map<String, String> hidden(String html) {
        var values = new LinkedHashMap<String, String>();
        var match =
                java.util.regex.Pattern.compile(
                                "<input[^>]*type=\"hidden\"[^>]*name=\"([^\"]+)\"[^>]*value=\"([^\"]*)\"")
                        .matcher(html);
        while (match.find()) values.put(match.group(1), unescape(match.group(2)));
        return values;
    }

    String issuerOrigin() {
        var issuer = java.net.URI.create(issuerSettings.getIssuer());
        return issuer.getScheme() + "://" + issuer.getRawAuthority();
    }

    Map<String, String> profileFields(String html) {
        var fields = hidden(html);
        fields.put("firstName", "Updated");
        fields.put("lastName", "Owned");
        fields.put("email", "updated@example.test");
        fields.put("confirmEmail", "updated@example.test");
        fields.put("phoneNumber", "+19999999998");
        fields.put("secondaryPhoneNumber", "");
        fields.put("x-request-datetime", Instant.now().toString());
        return fields;
    }

    @Test
    void transitionGrantsNothingAndLocalRolelessFormUsesOnlySessionProof() throws Exception {
        var fixture = hosted("hosted-view-" + UUID.randomUUID().toString().substring(0, 8));
        var anonymous = new HostedBrowser();
        var transition = anonymous.send("GET", fixture.launch(), null, null);
        assertThat(transition.statusCode()).isEqualTo(200);
        assertThat(transition.body())
                .doesNotContain("updated@example.test", fixture.access().value());
        String formPath = continuePath(transition.body());
        assertThat(anonymous.send("GET", formPath, null, null).statusCode()).isEqualTo(403);
        var form = fixture.browser().send("GET", formPath, null, null);
        assertThat(form.statusCode()).isEqualTo(200);
        assertThat(hidden(form.body()).keySet()).contains("nonce", "_csrf");
        assertThat(form.body())
                .doesNotContain(
                        fixture.access().value(),
                        Base64.getEncoder().encodeToString(fixture.access().key()),
                        "/oauth2/sso",
                        "auth-forms.js");
        assertThat(form.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }

    @Test
    void profileRequiresCsrfOriginNonceAndBindsCurrentIdentity() throws Exception {
        var fixture = hosted("hosted-denials-" + UUID.randomUUID().toString().substring(0, 8));
        String path =
                continuePath(fixture.browser().send("GET", fixture.launch(), null, null).body());
        var form = fixture.browser().send("GET", path, null, null);
        assertThat(form.statusCode()).isEqualTo(200);
        var fields = profileFields(form.body());
        var noCsrf = new LinkedHashMap<>(fields);
        noCsrf.remove("_csrf");
        assertThat(
                        fixture.browser()
                                .send("POST", "/portal/account/profile", noCsrf, issuerOrigin())
                                .statusCode())
                .isEqualTo(403);
        assertThat(
                        fixture.browser()
                                .send(
                                        "POST",
                                        "/portal/account/profile",
                                        fields,
                                        "https://evil.test")
                                .statusCode())
                .isEqualTo(403);
        var wrong = new LinkedHashMap<>(fields);
        wrong.put("nonce", "A".repeat(43));
        assertThat(
                        fixture.browser()
                                .send("POST", "/portal/account/profile", wrong, issuerOrigin())
                                .statusCode())
                .isEqualTo(400);
        var other = new LinkedHashMap<>(fields);
        other.put("expectedLoginId", UUID.randomUUID().toString());
        assertThat(
                        fixture.browser()
                                .send("POST", "/portal/account/profile", other, issuerOrigin())
                                .statusCode())
                .isEqualTo(403);
        assertThat(
                        sql.queryForObject(
                                "SELECT FirstName FROM Person.Profile WHERE ProfileId=?",
                                String.class,
                                fixture.user().getId()))
                .isEqualTo("Policy");
        var success =
                fixture.browser().send("POST", "/portal/account/profile", fields, issuerOrigin());
        assertThat(success.statusCode()).isEqualTo(303);
        assertThat(
                        sql.queryForObject(
                                "SELECT FirstName FROM Person.Profile WHERE ProfileId=?",
                                String.class,
                                fixture.user().getId()))
                .isEqualTo("Updated");
        assertThat(
                        fixture.browser()
                                .send("POST", "/portal/account/profile", fields, issuerOrigin())
                                .statusCode())
                .isEqualTo(400);
    }

    String passwordPath(HostedFixture fixture) throws Exception {
        return continuePath(
                fixture.browser()
                        .send(
                                "GET",
                                fixture.launch().replace("action=profile", "action=password"),
                                null,
                                null)
                        .body());
    }

    Map<String, String> passwordFields(String html, String current, String next) {
        var fields = hidden(html);
        fields.put(
                "currentPassword",
                Base64.getEncoder()
                        .encodeToString(current.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        fields.put(
                "newPassword",
                Base64.getEncoder()
                        .encodeToString(next.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        fields.put("confirmPassword", fields.get("newPassword"));
        fields.put("x-request-datetime", Instant.now().toString());
        return fields;
    }

    @Test
    void passwordChangesOnlyStableLoginAndRevokesAllItsHumanSessions() throws Exception {
        var fixture = hosted("hosted-password-" + UUID.randomUUID().toString().substring(0, 8));
        var details = userService.getByGuid(fixture.user().getRowGuid()).orElseThrow();
        String next = "T9!pK" + UUID.randomUUID().toString().replace("-", "") + "rX";
        var second = application(orgA);
        second.setName("Second client");
        var secondToken = token(second, details.username(), "openid");
        String brand = "other-" + UUID.randomUUID().toString().substring(0, 8);
        UUID source = UUID.randomUUID();
        sql.update(
                "INSERT dbo.ExternalSource(SourceId,SourceCode,ExternalTypeId) SELECT"
                        + " ?,?,ExternalTypeId FROM dbo.ExternalType WHERE ExternalTypeName='IDP'",
                source,
                brand);
        int otherCredential =
                sql.queryForObject(
                        "INSERT"
                            + " Person.Credential(ProfileId,UserName,Password,CredentialLocked,AccessFailedCount,DataOriginId,SyncFlag,ExternalId,Version)"
                            + " OUTPUT INSERTED.Id SELECT"
                            + " ProfileId,UserName,Password,0,0,DataOriginId,0,?,Version FROM"
                            + " Person.Credential WHERE RowGuid=?",
                        Integer.class,
                        source,
                        details.loginId());
        sql.update(
                "INSERT Person.CredentialAudit(CredentialId,Password,Version) SELECT"
                        + " Id,Password,Version FROM Person.Credential WHERE Id=?",
                otherCredential);
        byte[] untouched =
                sql.queryForObject(
                        "SELECT Password FROM Person.Credential WHERE Id=?",
                        byte[].class,
                        otherCredential);
        int auditCount =
                sql.queryForObject(
                        "SELECT COUNT(*) FROM Person.CredentialAudit WHERE CredentialId=?",
                        Integer.class,
                        otherCredential);
        byte[] siblingPassword =
                java.util.HexFormat.of()
                        .parseHex(
                                passwordFactory.encode(
                                        Base64.getEncoder()
                                                .encodeToString(
                                                        next.getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8))));
        int sameSource =
                sql.queryForObject(
                        "INSERT"
                            + " Person.Credential(ProfileId,UserName,Password,CredentialLocked,AccessFailedCount,DataOriginId,SyncFlag,ExternalId,Version)"
                            + " OUTPUT INSERTED.Id SELECT ProfileId,?,"
                            + " ?,0,0,DataOriginId,0,ExternalId,Version FROM Person.Credential"
                            + " WHERE RowGuid=?",
                        Integer.class,
                        "sibling-" + UUID.randomUUID(),
                        siblingPassword,
                        details.loginId());
        sql.update(
                "INSERT Person.CredentialAudit(CredentialId,Password,Version) SELECT"
                        + " Id,Password,Version FROM Person.Credential WHERE Id=?",
                sameSource);
        int siblingHistory =
                sql.queryForObject(
                        "SELECT COUNT(*) FROM Person.CredentialAudit WHERE CredentialId=?",
                        Integer.class,
                        sameSource);
        var form = fixture.browser().send("GET", passwordPath(fixture), null, null);
        assertThat(form.statusCode()).isEqualTo(200);
        var response =
                fixture.browser()
                        .send(
                                "POST",
                                "/portal/account/password",
                                passwordFields(form.body(), fixture.password(), next),
                                issuerOrigin());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("Password changed")
                .doesNotContain(next, fixture.password(), fixture.access().value());
        var updated =
                userService
                        .getUserAuthDetailsByUsernameAndExternalSourceCode(
                                details.username(), "idp")
                        .orElseThrow();
        assertThat(
                        passwordFactory.matches(
                                Base64.getEncoder()
                                        .encodeToString(
                                                next.getBytes(
                                                        java.nio.charset.StandardCharsets.UTF_8)),
                                updated.getPassword(),
                                updated.getVersion()))
                .isTrue();
        assertThat(
                        passwordFactory.matches(
                                Base64.getEncoder()
                                        .encodeToString(
                                                fixture.password()
                                                        .getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8)),
                                updated.getPassword(),
                                updated.getVersion()))
                .isFalse();
        assertThat(
                        Arrays.equals(
                                untouched,
                                sql.queryForObject(
                                        "SELECT Password FROM Person.Credential WHERE Id=?",
                                        byte[].class,
                                        otherCredential)))
                .isTrue();
        assertThat(
                        sql.queryForObject(
                                "SELECT COUNT(*) FROM Person.CredentialAudit WHERE CredentialId=?",
                                Integer.class,
                                otherCredential))
                .isEqualTo(auditCount);
        assertThat(
                        Arrays.equals(
                                siblingPassword,
                                sql.queryForObject(
                                        "SELECT Password FROM Person.Credential WHERE Id=?",
                                        byte[].class,
                                        sameSource)))
                .isTrue();
        assertThat(
                        sql.queryForObject(
                                "SELECT COUNT(*) FROM Person.CredentialAudit WHERE CredentialId=?",
                                Integer.class,
                                sameSource))
                .isEqualTo(siblingHistory);
        assertThat(call(fixture.access(), "GET", "/api/v1/portal/context", null).statusCode())
                .isEqualTo(401);
        assertThat(call(secondToken, "GET", "/api/v1/portal/context", null).statusCode())
                .isEqualTo(401);
        assertThat(
                        sql.queryForObject(
                                "SELECT COUNT(*) FROM Token.AuthSession WHERE SubjectId=? AND"
                                        + " Branding='idp' AND AuthSessionStatusId=?",
                                Integer.class,
                                details.username(),
                                com.chellavignesh.authserver.enums.entity.AuthSessionStatusEnum
                                        .ACTIVE
                                        .getValue()))
                .isZero();
    }

    byte[] passwordBytes(HostedFixture f) {
        return sql.queryForObject(
                "SELECT Password FROM Person.Credential WHERE ProfileId=? AND"
                        + " ExternalId='11111111-1111-1111-1111-111111111111'",
                byte[].class,
                f.user().getId());
    }

    int historyCount(HostedFixture f) {
        return sql.queryForObject(
                "SELECT COUNT(*) FROM Person.CredentialAudit h JOIN Person.Credential c ON"
                        + " c.Id=h.CredentialId WHERE c.ProfileId=? AND"
                        + " c.ExternalId='11111111-1111-1111-1111-111111111111'",
                Integer.class,
                f.user().getId());
    }

    @Test
    void wrongCurrentConfirmationSyntaxAndScopedHistoryNeverWriteOrReflectPasswords()
            throws Exception {
        var f = hosted("hosted-invalid-" + UUID.randomUUID().toString().substring(0, 8));
        byte[] original = passwordBytes(f);
        int audit = historyCount(f);
        for (String kind :
                List.of("current", "confirmation", "syntax", "history", "semantic", "encoding")) {
            var form = f.browser().send("GET", passwordPath(f), null, null);
            assertThat(form.statusCode()).isEqualTo(200);
            String next =
                    kind.equals("history")
                            ? f.password()
                            : kind.equals("syntax")
                                    ? "short"
                                    : kind.equals("semantic")
                                            ? "P8!Policy"
                                                    + UUID.randomUUID().toString().replace("-", "")
                                                    + "rX"
                                            : "U7!pM"
                                                    + UUID.randomUUID().toString().replace("-", "")
                                                    + "rX";
            var fields =
                    passwordFields(
                            form.body(),
                            kind.equals("current") ? "Incorrect9!pR" : f.password(),
                            next);
            if (kind.equals("confirmation"))
                fields.put(
                        "confirmPassword",
                        Base64.getEncoder().encodeToString("Different8!pR".getBytes()));
            if (kind.equals("encoding")) fields.put("newPassword", "%invalid");
            var denied =
                    f.browser().send("POST", "/portal/account/password", fields, issuerOrigin());
            assertThat(denied.statusCode()).as("validation scenario %s", kind).isEqualTo(400);
            assertThat(denied.body())
                    .contains("Return to account", "http://127.0.0.1:3100/account");
            assertThat(denied.body()).doesNotContain(f.password(), next, fields.get("newPassword"));
            assertThat(Arrays.equals(original, passwordBytes(f))).isTrue();
            assertThat(historyCount(f)).isEqualTo(audit);
            assertThat(
                            f.browser()
                                    .send(
                                            "POST",
                                            "/portal/account/password",
                                            fields,
                                            issuerOrigin())
                                    .statusCode())
                    .isEqualTo(400);
        }
    }

    @Test
    void revocationFailureRollsBackPasswordHistoryAndAuthorizationState() throws Exception {
        var f = hosted("hosted-rollback-" + UUID.randomUUID().toString().substring(0, 8));
        byte[] original = passwordBytes(f);
        int audit = historyCount(f);
        String grant =
                installGrant(
                        f.access(),
                        userService.getByGuid(f.user().getRowGuid()).orElseThrow().username(),
                        AuthorizationGrantType.AUTHORIZATION_CODE);
        var form = f.browser().send("GET", passwordPath(f), null, null);
        String next = "R8!qV" + UUID.randomUUID().toString().replace("-", "") + "mX";
        doThrow(new IllegalStateException("Injected revocation fault"))
                .when(revocations)
                .revokeAllForLogin(any());
        var response =
                f.browser()
                        .send(
                                "POST",
                                "/portal/account/password",
                                passwordFields(form.body(), f.password(), next),
                                issuerOrigin());
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).doesNotContain(next, f.password(), "Injected");
        assertThat(Arrays.equals(original, passwordBytes(f))).isTrue();
        assertThat(historyCount(f)).isEqualTo(audit);
        assertThat(
                        sql.queryForObject(
                                "SELECT COUNT(*) FROM dbo.oauth2_authorization WHERE id=?",
                                Integer.class,
                                grant))
                .isEqualTo(1);
        var token =
                tokenService
                        .getByValue(
                                f.access().value(),
                                com.chellavignesh.authserver.enums.entity.TokenTypeEnum
                                        .ACCESS_TOKEN)
                        .orElseThrow();
        assertThat(tokenService.getClaimsForToken(token).get("active")).isEqualTo(true);
    }

    String installGrant(Fixture access, String username, AuthorizationGrantType type)
            throws Exception {
        String session =
                sql.queryForObject(
                        "SELECT CONVERT(VARCHAR(36),SessionId) FROM Token.Token WHERE Data=?",
                        String.class,
                        access.value());
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        var grant =
                OAuth2Authorization.withRegisteredClient(
                                applicationService.getRegisteredClientById(app.getId()))
                        .id(id)
                        .principalName(username)
                        .authorizationGrantType(type)
                        .attribute(JdbcOAuth2AuthorizationService.SESSION_ID, session)
                        .authorizedScopes(Set.of("openid"))
                        .accessToken(
                                new OAuth2AccessToken(
                                        OAuth2AccessToken.TokenType.BEARER,
                                        access.value(),
                                        now,
                                        now.plusSeconds(300),
                                        Set.of("openid")))
                        .build();
        new org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService(
                        named.getJdbcTemplate(), clients)
                .save(grant);
        return id;
    }

    @Test
    void passwordRevocationPreservesOtherBrandHumanAndMachineGrants() throws Exception {
        var f = hosted("hosted-grants-" + UUID.randomUUID().toString().substring(0, 8));
        var user = userService.getByGuid(f.user().getRowGuid()).orElseThrow();
        String targetGrant =
                installGrant(
                        f.access(), user.username(), AuthorizationGrantType.AUTHORIZATION_CODE);
        String brand = "preserved-" + UUID.randomUUID().toString().substring(0, 8);
        UUID source = UUID.randomUUID();
        sql.update(
                "INSERT dbo.ExternalSource(SourceId,SourceCode,ExternalTypeId) SELECT"
                        + " ?,?,ExternalTypeId FROM dbo.ExternalType WHERE ExternalTypeName='IDP'",
                source,
                brand);
        int profile =
                sql.queryForObject(
                        "INSERT"
                            + " Person.Profile(LoginProviderId,Email,EmailConfirmed,PhoneNumber,PhoneNumberConfirmed,TwoFactorEnabled,FirstName,LastName,DataOriginId,SyncFlag)"
                            + " OUTPUT INSERTED.ProfileId"
                            + " VALUES(1,'preserved@example.test',1,'+19999999998',1,0,'Preserved','Account',0,0)",
                        Integer.class);
        sql.update(
                "INSERT Person.ProfileOrganization(ProfileId,OrganizationId) VALUES(?,?)",
                profile,
                orgA.getId());
        int credential =
                sql.queryForObject(
                        "INSERT"
                            + " Person.Credential(ProfileId,UserName,Password,Version,CredentialLocked,AccessFailedCount,DataOriginId,SyncFlag,ExternalId)"
                            + " OUTPUT INSERTED.Id SELECT ?,UserName,Password,Version,0,0,0,0,?"
                            + " FROM Person.Credential WHERE RowGuid=?",
                        Integer.class,
                        profile,
                        source,
                        user.loginId());
        var other = token(app, user.username(), "openid");
        String otherSession =
                sql.queryForObject(
                        "SELECT CONVERT(VARCHAR(36),SessionId) FROM Token.Token WHERE Data=?",
                        String.class,
                        other.value());
        sql.update(
                "UPDATE Token.AuthSession SET Branding=? WHERE SessionId=?", brand, otherSession);
        String otherGrant =
                installGrant(other, user.username(), AuthorizationGrantType.AUTHORIZATION_CODE);
        sso.generateCookie(UUID.fromString(otherSession));
        var machine = token(app, app.getClientId(), "");
        String machineSession =
                sql.queryForObject(
                        "SELECT CONVERT(VARCHAR(36),SessionId) FROM Token.Token WHERE Data=?",
                        String.class,
                        machine.value());
        sql.update("UPDATE Token.AuthSession SET Branding=NULL WHERE SessionId=?", machineSession);
        String machineGrant =
                installGrant(machine, app.getClientId(), AuthorizationGrantType.CLIENT_CREDENTIALS);
        byte[] collateral =
                sql.queryForObject(
                        "SELECT Password FROM Person.Credential WHERE Id=?",
                        byte[].class,
                        credential);
        var form = f.browser().send("GET", passwordPath(f), null, null);
        String next = "V9!qR" + UUID.randomUUID().toString().replace("-", "") + "sP";
        assertThat(
                        f.browser()
                                .send(
                                        "POST",
                                        "/portal/account/password",
                                        passwordFields(form.body(), f.password(), next),
                                        issuerOrigin())
                                .statusCode())
                .isEqualTo(200);
        assertThat(
                        sql.queryForObject(
                                "SELECT COUNT(*) FROM dbo.oauth2_authorization WHERE id=?",
                                Integer.class,
                                targetGrant))
                .isZero();
        for (String id : List.of(otherGrant, machineGrant))
            assertThat(
                            sql.queryForObject(
                                    "SELECT COUNT(*) FROM dbo.oauth2_authorization WHERE id=?",
                                    Integer.class,
                                    id))
                    .isEqualTo(1);
        assertThat(
                        sql.queryForObject(
                                "SELECT COUNT(*) FROM Token.SSO WHERE SessionId=?",
                                Integer.class,
                                otherSession))
                .isEqualTo(1);
        for (var preserved : List.of(other, machine)) {
            var token =
                    tokenService
                            .getByValue(
                                    preserved.value(),
                                    com.chellavignesh.authserver.enums.entity.TokenTypeEnum
                                            .ACCESS_TOKEN)
                            .orElseThrow();
            assertThat(tokenService.getClaimsForToken(token).get("active")).isEqualTo(true);
        }
        assertThat(
                        Arrays.equals(
                                collateral,
                                sql.queryForObject(
                                        "SELECT Password FROM Person.Credential WHERE Id=?",
                                        byte[].class,
                                        credential)))
                .isTrue();
    }

    @Test
    void providerModeIsCheckedAgainOnPostAndUnknownModeDoesNotWrite() throws Exception {
        var f = hosted("hosted-managed-" + UUID.randomUUID().toString().substring(0, 8));
        String path = continuePath(f.browser().send("GET", f.launch(), null, null).body());
        var form = f.browser().send("GET", path, null, null);
        var fields = profileFields(form.body());
        for (String column : List.of("Credential", "Profile")) {
            sql.update(
                    "UPDATE Person." + column + " SET SyncFlag=1 WHERE ProfileId=?",
                    f.user().getId());
            assertThat(f.browser().send("GET", path, null, null).statusCode()).isEqualTo(403);
            assertThat(
                            f.browser()
                                    .send("POST", "/portal/account/profile", fields, issuerOrigin())
                                    .statusCode())
                    .isEqualTo(403);
            sql.update(
                    "UPDATE Person." + column + " SET SyncFlag=0 WHERE ProfileId=?",
                    f.user().getId());
        }
        assertThat(
                        sql.queryForObject(
                                "SELECT FirstName FROM Person.Profile WHERE ProfileId=?",
                                String.class,
                                f.user().getId()))
                .isEqualTo("Policy");
    }

    @Test
    void exactLaunchBrowserAndSessionFailuresNeverRenderAnotherAccount() throws Exception {
        var f = hosted("hosted-boundary-" + UUID.randomUUID().toString().substring(0, 8));
        String path = continuePath(f.browser().send("GET", f.launch(), null, null).body());
        var form = f.browser().send("GET", path, null, null);
        assertThat(form.statusCode()).isEqualTo(200);
        assertThat(
                        java.util.regex.Pattern.compile("name=\"_csrf\"")
                                .matcher(form.body())
                                .results()
                                .count())
                .isEqualTo(1);
        var fields = profileFields(form.body());
        String ssoCookie = f.browser().cookies.remove("ags-idp-session");
        assertThat(f.browser().send("GET", path, null, null).statusCode()).isEqualTo(403);
        assertThat(
                        f.browser()
                                .send("POST", "/portal/account/profile", fields, issuerOrigin())
                                .statusCode())
                .isEqualTo(403);
        f.browser().cookies.put("ags-idp-session", "invalid-cookie");
        assertThat(f.browser().send("GET", path, null, null).statusCode()).isEqualTo(403);
        f.browser().cookies.put("ags-idp-session", ssoCookie);
        f.browser().refererOverride = "https://foreign.test/";
        assertThat(f.browser().send("GET", path, null, null).statusCode()).isEqualTo(403);
        f.browser().refererOverride = "omit";
        assertThat(f.browser().send("GET", path, null, null).statusCode()).isEqualTo(403);
        f.browser().refererOverride = null;
        f.browser().userAgent = "different";
        assertThat(f.browser().send("GET", path, null, null).statusCode()).isEqualTo(403);
        f.browser().userAgent = "PolicyIntegration/1.0";
        f.browser().language = "different";
        assertThat(f.browser().send("GET", path, null, null).statusCode()).isEqualTo(403);
        f.browser().language = "en-US";
        assertThat(f.browser().send("POST", "/portal/account/profile", fields, null).statusCode())
                .isEqualTo(403);
        String offset =
                java.net.URLEncoder.encode(
                        java.time.OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30)).toString(),
                        java.nio.charset.StandardCharsets.UTF_8);
        assertThat(
                        f.browser()
                                .send(
                                        "GET",
                                        path.replaceFirst(
                                                "x-request-datetime=[^&]+",
                                                "x-request-datetime=" + offset),
                                        null,
                                        null)
                                .statusCode())
                .isEqualTo(403);
        for (String unsafe :
                List.of(
                        path + "&code=unexpected",
                        path.replaceFirst(
                                "returnTo=[^&]+", "returnTo=https%3A%2F%2Fforeign.test%2Faccount"),
                        path + "&clientId=duplicate"))
            assertThat(f.browser().send("GET", unsafe, null, null).statusCode()).isEqualTo(400);
        var other = hosted("hosted-other-" + UUID.randomUUID().toString().substring(0, 8));
        var mismatch = other.browser().send("GET", path, null, null);
        assertThat(mismatch.statusCode()).isEqualTo(403);
        assertThat(mismatch.body())
                .doesNotContain(
                        userService.getByGuid(f.user().getRowGuid()).orElseThrow().email(),
                        userService.getByGuid(other.user().getRowGuid()).orElseThrow().email());
        String id =
                sql.queryForObject(
                        "SELECT CONVERT(VARCHAR(36),SessionId) FROM Token.Token WHERE Data=?",
                        String.class,
                        f.access().value());
        sql.update(
                "UPDATE Token.AuthSession SET AuthSessionStatusId=? WHERE SessionId=?",
                com.chellavignesh.authserver.enums.entity.AuthSessionStatusEnum.INACTIVE.getValue(),
                id);
        assertThat(f.browser().send("GET", path, null, null).statusCode()).isEqualTo(403);
        assertThat(
                        sql.queryForObject(
                                "SELECT FirstName FROM Person.Profile WHERE ProfileId=?",
                                String.class,
                                f.user().getId()))
                .isEqualTo("Policy");
    }

    @Test
    void sharedPlatformClientAllowsRolelessLocalTenantSelfServiceOnlyWhileIdentityActive()
            throws Exception {
        var tenant = organization("Hosted tenant " + UUID.randomUUID());
        var f = hosted("hosted-tenant-" + UUID.randomUUID().toString().substring(0, 8), tenant);
        assertThat(tenant.getId()).isNotEqualTo(orgA.getId());
        assertThat(userService.getByGuid(f.user().getRowGuid()).orElseThrow().orgId())
                .isEqualTo(tenant.getRowGuid());
        assertThat(
                        sql.queryForObject(
                                "SELECT COUNT(*) FROM Person.ProfileGroup WHERE ProfileId=?",
                                Integer.class,
                                f.user().getId()))
                .isZero();
        String path = continuePath(f.browser().send("GET", f.launch(), null, null).body());
        var form = f.browser().send("GET", path, null, null);
        assertThat(form.statusCode()).isEqualTo(200);
        assertThat(
                        f.browser()
                                .send(
                                        "POST",
                                        "/portal/account/profile",
                                        profileFields(form.body()),
                                        issuerOrigin())
                                .statusCode())
                .isEqualTo(303);
        assertThat(
                        sql.queryForObject(
                                "SELECT FirstName FROM Person.Profile WHERE ProfileId=?",
                                String.class,
                                f.user().getId()))
                .isEqualTo("Updated");
        var password = f.browser().send("GET", passwordPath(f), null, null);
        assertThat(password.statusCode()).isEqualTo(200);
        assertThat(
                        f.browser()
                                .send(
                                        "POST",
                                        "/portal/account/password",
                                        passwordFields(
                                                password.body(),
                                                f.password(),
                                                "N9!bR"
                                                        + UUID.randomUUID()
                                                                .toString()
                                                                .replace("-", "")
                                                        + "qZ"),
                                        issuerOrigin())
                                .statusCode())
                .isEqualTo(200);
        assertThat(call(f.access(), "GET", "/api/v1/portal/context", null).statusCode())
                .isEqualTo(401);
    }

    @Test
    void newGetReplacesOldFormAndConcurrentHttpNonceAllowsExactlyOneMutation() throws Exception {
        var f = hosted("hosted-nonce-http-" + UUID.randomUUID().toString().substring(0, 8));
        String path = continuePath(f.browser().send("GET", f.launch(), null, null).body());
        var old = f.browser().send("GET", path, null, null);
        var current = f.browser().send("GET", path, null, null);
        assertThat(
                        f.browser()
                                .send(
                                        "POST",
                                        "/portal/account/profile",
                                        profileFields(old.body()),
                                        issuerOrigin())
                                .statusCode())
                .isEqualTo(400);
        var password = f.browser().send("GET", passwordPath(f), null, null);
        assertThat(password.statusCode()).isEqualTo(200);
        var fields = profileFields(current.body());
        var wrongAction = new LinkedHashMap<>(hidden(password.body()));
        wrongAction.putAll(fields);
        wrongAction.put("nonce", hidden(password.body()).get("nonce"));
        assertThat(
                        f.browser()
                                .send(
                                        "POST",
                                        "/portal/account/profile",
                                        wrongAction,
                                        issuerOrigin())
                                .statusCode())
                .isEqualTo(400);
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                var browser = new HostedBrowser();
                browser.cookies.putAll(f.browser().cookies);
                results.add(
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    start.await();
                                    return browser.send(
                                                    "POST",
                                                    "/portal/account/profile",
                                                    fields,
                                                    issuerOrigin())
                                            .statusCode();
                                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(
                            List.of(
                                    results.get(0).get(20, TimeUnit.SECONDS),
                                    results.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(303, 400);
        }
        assertThat(
                        sql.queryForObject(
                                "SELECT FirstName FROM Person.Profile WHERE ProfileId=?",
                                String.class,
                                f.user().getId()))
                .isEqualTo("Updated");
    }

    @Test
    void heldCredentialLockDoesNotSerializeUnrelatedHumanOrMachineGrants() throws Exception {
        var held = hosted("hosted-held-" + UUID.randomUUID().toString().substring(0, 8));
        var other = hosted("hosted-free-" + UUID.randomUUID().toString().substring(0, 8));
        String humanId =
                installGrant(
                        other.access(),
                        userService.getByGuid(other.user().getRowGuid()).orElseThrow().username(),
                        AuthorizationGrantType.AUTHORIZATION_CODE);
        var machine = token(app, app.getClientId(), "");
        String machineSession =
                sql.queryForObject(
                        "SELECT CONVERT(VARCHAR(36),SessionId) FROM Token.Token WHERE Data=?",
                        String.class,
                        machine.value());
        sql.update("UPDATE Token.AuthSession SET Branding=NULL WHERE SessionId=?", machineSession);
        String machineId =
                installGrant(machine, app.getClientId(), AuthorizationGrantType.CLIENT_CREDENTIALS);
        var human = grants.findById(humanId);
        var machineGrant = grants.findById(machineId);
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var holder =
                    executor.submit(
                            () ->
                                    new org.springframework.transaction.support.TransactionTemplate(
                                                    transactionManager)
                                            .execute(
                                                    status -> {
                                                        credentialLock.acquire(
                                                                userService
                                                                        .getByGuid(
                                                                                held.user()
                                                                                        .getRowGuid())
                                                                        .orElseThrow()
                                                                        .loginId());
                                                        locked.countDown();
                                                        try {
                                                            if (!release.await(
                                                                    10, TimeUnit.SECONDS))
                                                                throw new IllegalStateException(
                                                                        "Test barrier expired");
                                                        } catch (InterruptedException e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                        return null;
                                                    }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                executor.submit(
                                () -> {
                                    grants.save(human);
                                    grants.save(machineGrant);
                                })
                        .get(3, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            holder.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        assertThat(grants.findById(humanId)).isNotNull();
        assertThat(grants.findById(machineId)).isNotNull();
    }

    @Test
    void actualOversizedChunkedAndWrongContentTypePostsAreRejectedBeforeMutation()
            throws Exception {
        var f = hosted("hosted-body-" + UUID.randomUUID().toString().substring(0, 8));
        for (boolean chunked : new boolean[] {false, true}) {
            byte[] body = new byte[65537];
            Arrays.fill(body, (byte) 'x');
            var publisher =
                    chunked
                            ? java.net.http.HttpRequest.BodyPublishers.ofInputStream(
                                    () -> new java.io.ByteArrayInputStream(body))
                            : java.net.http.HttpRequest.BodyPublishers.ofByteArray(body);
            var request =
                    java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create(
                                            "http://localhost:"
                                                    + port
                                                    + "/services/portal/account/profile"))
                            .timeout(Duration.ofSeconds(15))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(publisher)
                            .build();
            assertThat(
                            http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                                    .statusCode())
                    .isEqualTo(413);
        }
        var request =
                java.net.http.HttpRequest.newBuilder(
                                java.net.URI.create(
                                        "http://localhost:"
                                                + port
                                                + "/services/portal/account/profile"))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
                        .build();
        assertThat(
                        http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                                .statusCode())
                .isEqualTo(415);
        assertThat(
                        sql.queryForObject(
                                "SELECT FirstName FROM Person.Profile WHERE ProfileId=?",
                                String.class,
                                f.user().getId()))
                .isEqualTo("Policy");
    }
}
