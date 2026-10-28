package com.chellavignesh.authserver.database;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.user.UserStatus;
import com.chellavignesh.authserver.config.*;
import com.chellavignesh.authserver.config.JdbcOAuth2AuthorizationService;
import com.chellavignesh.authserver.enums.entity.AuthSessionStatusEnum;
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
    @MockitoSpyBean PasswordEncoderManager passwordEncoderManager;
    @Autowired com.chellavignesh.authserver.token.TokenService tokenService;

    @MockitoSpyBean
    com.chellavignesh.authserver.adminportal.hosted.HostedAccountRevocationRepository revocations;

    @Autowired com.chellavignesh.authserver.adminportal.user.UserService userService;
    @Autowired JdbcOAuth2AuthorizationService grants;
    @Autowired NamedParameterJdbcTemplate named;
    @Autowired RegisteredClientRepository clients;
    @Autowired CredentialAuthenticationLock credentialLock;
    @Autowired com.chellavignesh.authserver.security.ManagementAccessPolicy managementPolicy;
    @Autowired com.chellavignesh.authserver.adminportal.externalsource.ExternalSourceService externalSources;
    @Autowired com.chellavignesh.authserver.adminportal.credential.CredentialService machineCredentials;
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
        reset(applicationService, revocations, passwordEncoderManager);
        SecurityContextHolder.clearContext();
    }

    MockHttpServletRequest loginRequest(String branding, String password) {
        var authorize = new MockHttpServletRequest("GET", "/services/oauth2/authorize");
        authorize.addParameter("client_id", app.getClientId());
        authorize.addParameter("scope", "openid profile");
        var request = new MockHttpServletRequest("POST", "/services/login");
        request.addParameter(
                "password",
                Base64.getEncoder()
                        .encodeToString(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        request
                .getSession()
                .setAttribute(
                        "SPRING_SECURITY_SAVED_REQUEST",
                        new DefaultSavedRequest(
                                authorize, new org.springframework.security.web.PortResolverImpl()));
        request.getSession().setAttribute(ApplicationConstants.BRANDING_INFO, branding);
        request
                .getSession()
                .setAttribute(ApplicationConstants.CLIENT_FINGERPRINT, new byte[] {1, 2, 3});
        return request;
    }

    MockHttpServletResponse authenticateSnapshot(
            CustomUserDetails captured, String branding, String password) throws Exception {
        var response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(
                loginRequest(branding, password),
                response,
                UsernamePasswordAuthenticationToken.authenticated(captured, null, List.of()));
        return response;
    }

    UUID siblingCredential(HostedFixture base, String username, UUID source) {
        return UUID.fromString(
                sql.queryForObject(
                        "INSERT"
                                + " Person.Credential(ProfileId,UserName,Password,Version,CredentialLocked,AccessFailedCount,DataOriginId,SyncFlag,ExternalId)"
                                + " OUTPUT CONVERT(VARCHAR(36),INSERTED.RowGuid) SELECT"
                                + " ProfileId,?,Password,Version,0,0,DataOriginId,0,? FROM Person.Credential WHERE"
                                + " RowGuid=?",
                        String.class,
                        username,
                        source,
                        base.user().getLoginId()));
    }

    HostedFixture hostedFromCredential(
            HostedFixture base, Fixture access, UUID login, String branding) throws Exception {
        String id =
                sql.queryForObject(
                        "SELECT CONVERT(VARCHAR(36),SessionId) FROM Token.Token WHERE Data=?",
                        String.class,
                        access.value());
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
                "UPDATE Token.AuthSession SET LoginId=?,Branding=?,ClientFingerprint=?,RedirectUriId=? WHERE"
                        + " SessionId=?",
                login,
                branding,
                fingerprint,
                redirect,
                id);
        var browser = new HostedBrowser();
        browser.cookies.put(
                "ags-idp-session",
                Base64.getEncoder()
                        .encodeToString(sso.generateCookie(UUID.fromString(id)).encryptedSessionId()));
        var fields = new LinkedHashMap<String, String>();
        fields.put("action", "profile");
        fields.put("clientId", app.getClientId());
        fields.put("expectedLoginId", login.toString());
        fields.put("returnTo", "http://127.0.0.1:3100/account");
        fields.put("x-request-datetime", Instant.now().toString());
        return new HostedFixture(
                base.user(), browser, "/portal/account-action?" + encode(fields), access, base.password());
    }

    void siblingIdentityScenario(boolean otherSource) throws Exception {
        var base = hosted("fix1-sibling-" + UUID.randomUUID().toString().substring(0, 8));
        sql.update(
                "UPDATE Person.Profile SET TwoFactorEnabled=0 WHERE ProfileId=?", base.user().getId());
        var original = userService.getByGuid(base.user().getRowGuid()).orElseThrow();
        String branding = otherSource ? "fix1-" + UUID.randomUUID().toString().substring(0, 8) : "idp";
        UUID source = UUID.fromString("11111111-1111-1111-1111-111111111111");
        if (otherSource) {
            source = UUID.randomUUID();
            sql.update(
                    "INSERT dbo.ExternalSource(SourceId,SourceCode,ExternalTypeId) SELECT ?,?,ExternalTypeId"
                            + " FROM dbo.ExternalType WHERE ExternalTypeName='IDP'",
                    source,
                    branding);
        }
        String username = otherSource ? original.username() : original.username() + "-second";
        UUID login = siblingCredential(base, username, source);
        var captured =
                (CustomUserDetails)
                        userDetails.loadUserByUsername(username + System.lineSeparator() + branding);
        assertThat(captured.getLoginId()).isEqualTo(login);
        assertThat(
                        passwordFactory.matches(
                                Base64.getEncoder().encodeToString(base.password().getBytes()),
                                captured.getUserAuthDetails().getPassword(),
                                captured.getUserAuthDetails().getVersion()))
                .isTrue();
        assertThat(authenticateSnapshot(captured, branding, base.password()).getStatus())
                .isEqualTo(302);
        var access = token(app, username, "openid profile metadata idp-admin");
        String session =
                sql.queryForObject(
                        "SELECT CONVERT(VARCHAR(36),SessionId) FROM Token.Token WHERE Data=?",
                        String.class,
                        access.value());
        sql.update("UPDATE Token.AuthSession SET Branding=?,LoginId=? WHERE SessionId=?", branding, login, session);
        sql.update(
                "EXEC Person.CreateMetadata ?,?,?",
                base.user().getId(),
                "uuid_login_id",
                original.loginId().toString());
        sql.update(
                "EXEC Person.CreateMetadata ?,?,?",
                base.user().getId(),
                "uuid_member_id",
                UUID.randomUUID().toString());
        sql.update(
                "EXEC Person.CreateMetadata ?,?,?",
                base.user().getId(),
                "login_guid",
                original.loginId().toString());
        var claims =
                tokenService.getClaimsForToken(
                        tokenService
                                .getByValue(
                                        access.value(),
                                        com.chellavignesh.authserver.enums.entity.TokenTypeEnum.ACCESS_TOKEN)
                                .orElseThrow());
        assertThat(claims.get("login_guid")).isEqualTo(login);
        var response = call(access, "GET", "/api/v1/portal/context", null);
        assertThat(response.statusCode()).isEqualTo(200);
        var context = mapper.readTree(response.body());
        assertThat(context.path("subject").path("loginId").asText()).isEqualTo(login.toString());
        assertThat(context.path("subject").path("username").asText()).isEqualTo(username);
        var f = hostedFromCredential(base, access, login, branding);
        var profile =
                f.browser()
                        .send(
                                "GET",
                                continuePath(f.browser().send("GET", f.launch(), null, null).body()),
                                null,
                                null);
        assertThat(profile.statusCode()).isEqualTo(200);
        assertThat(
                        f.browser()
                                .send(
                                        "POST",
                                        "/portal/account/profile",
                                        profileFields(profile.body()),
                                        issuerOrigin())
                                .statusCode())
                .isEqualTo(303);
        var password = f.browser().send("GET", passwordPath(f), null, null);
        assertThat(password.statusCode()).isEqualTo(200);
        String next = "X8!fR" + UUID.randomUUID().toString().replace("-", "") + "qZ";
        assertThat(
                        f.browser()
                                .send(
                                        "POST",
                                        "/portal/account/password",
                                        passwordFields(password.body(), base.password(), next),
                                        issuerOrigin())
                                .statusCode())
                .isEqualTo(200);
        assertThat(authenticateSnapshot(captured, branding, base.password()).getStatus())
                .isEqualTo(401);
        var sibling =
                (CustomUserDetails)
                        userDetails.loadUserByUsername(original.username() + System.lineSeparator() + "idp");
        assertThat(sibling.getLoginId()).isEqualTo(original.loginId());
        assertThat(
                        passwordFactory.matches(
                                Base64.getEncoder().encodeToString(base.password().getBytes()),
                                sibling.getUserAuthDetails().getPassword(),
                                sibling.getUserAuthDetails().getVersion()))
                .isTrue();
        assertThat(authenticateSnapshot(sibling, "idp", base.password()).getStatus()).isEqualTo(302);
    }

    @Test
    void sameSourceSiblingLoginContextAndHostedMutationsUseExactCredential() throws Exception {
        siblingIdentityScenario(false);
    }

    @Test
    void differentSourceSiblingContextAndHostedMutationsIgnoreMutableMetadata() throws Exception {
        siblingIdentityScenario(true);
    }

    @Test
    void biometricProfileProofDeniesSameSourceAmbiguityButKeepsOtherSourcesAndProfileCrud()
            throws Exception {
        var base = hosted("fix1-biometric-" + UUID.randomUUID().toString().substring(0, 8));
        assertThat(userService.getUniqueBiometricIdentity(base.user().getRowGuid(), "idp")).isPresent();
        String brand = "bio-" + UUID.randomUUID().toString().substring(0, 8);
        UUID source = UUID.randomUUID();
        sql.update(
                "INSERT dbo.ExternalSource(SourceId,SourceCode,ExternalTypeId) SELECT ?,?,ExternalTypeId"
                        + " FROM dbo.ExternalType WHERE ExternalTypeName='IDP'",
                source,
                brand);
        UUID other = siblingCredential(base, "bio-other-" + UUID.randomUUID(), source);
        assertThat(userService.getUniqueBiometricIdentity(base.user().getRowGuid(), "idp"))
                .get()
                .extracting(com.chellavignesh.authserver.adminportal.user.entity.UserDetails::loginId)
                .isEqualTo(base.user().getLoginId());
        assertThat(userService.getUniqueBiometricIdentity(base.user().getRowGuid(), brand))
                .get()
                .extracting(com.chellavignesh.authserver.adminportal.user.entity.UserDetails::loginId)
                .isEqualTo(other);
        UUID ambiguous = siblingCredential(
                base,
                "bio-sibling-" + UUID.randomUUID(),
                UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(userService.getUniqueBiometricIdentity(base.user().getRowGuid(), "idp")).isEmpty();
        assertThat(userService.getByGuid(base.user().getRowGuid())).isPresent();
        assertThat(userService.getByGuidAndBranding(base.user().getRowGuid(), "idp")).isPresent();
        assertThat(userService.getUniqueBiometricIdentity(base.user().getRowGuid(), brand)).isPresent();
        sql.update("UPDATE Person.Credential SET CredentialLocked=1 WHERE RowGuid=?", ambiguous);
        assertThat(userService.getUniqueBiometricIdentity(base.user().getRowGuid(), "idp")).isPresent();
    }

    @Test
    void sqlUpgradeRollbackAbortsBeforeSessionEvenAfterConcurrentPasswordCommit() throws Exception {
        var f = hosted("fix1-upgrade-fault-" + UUID.randomUUID().toString().substring(0, 8));
        sql.update("UPDATE Person.Profile SET TwoFactorEnabled=0 WHERE ProfileId=?", f.user().getId());
        // Valid legacy storage envelope: four-byte cipher index followed by opaque cipher bytes.
        // This is a post-authenticated snapshot fixture, not external v0 password verification.
        sql.update(
                "UPDATE Person.Credential SET Password=0x00000007AABBCCDDEEFF00112233445566778899,Version=0"
                        + " WHERE ProfileId=?",
                f.user().getId());
        var snapshot =
                (CustomUserDetails)
                        userDetails.loadUserByUsername(
                                userService.getByGuid(f.user().getRowGuid()).orElseThrow().username()
                                        + System.lineSeparator()
                                        + "idp");
        String username = snapshot.getUsername();
        int initial =
                sql.queryForObject(
                        "SELECT COUNT(*) FROM Token.AuthSession WHERE SubjectId=?", Integer.class, username);
        CountDownLatch rolledBack = new CountDownLatch(1), passwordCommitted = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            named.getJdbcTemplate().execute("ROLLBACK TRANSACTION");
                            rolledBack.countDown();
                            if (!passwordCommitted.await(10, TimeUnit.SECONDS))
                                throw new IllegalStateException("Test barrier expired");
                            throw new com.chellavignesh.authserver.session.exception
                                    .FailedToProcessPasswordException("Injected upgrade SQL failure");
                        })
                .when(passwordEncoderManager)
                .processPassword(any(), anyString(), eq("idp"));
        MockHttpServletResponse result;
        try (var executor = Executors.newSingleThreadExecutor()) {
            var queued = executor.submit(() -> authenticateSnapshot(snapshot, "idp", f.password()));
            assertThat(rolledBack.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                        .execute(
                                status -> {
                                    credentialLock.acquire(snapshot.getLoginId());
                                    sql.update(
                                            "UPDATE Person.Credential SET Password=0xAABBCCDD,Version=? WHERE RowGuid=?",
                                            PasswordEncoderFactory.currentVersion,
                                            snapshot.getLoginId());
                                    revocations.revokeAllForLogin(snapshot.getLoginId());
                                    return null;
                                });
            } finally {
                passwordCommitted.countDown();
            }
            result = queued.get(15, TimeUnit.SECONDS);
        } finally {
            passwordCommitted.countDown();
        }
        assertThat(result.getStatus()).isEqualTo(503);
        assertThat(result.getRedirectedUrl()).isNull();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM dbo.oauth2_authorization WHERE principal_name=?", Integer.class, username)).isZero();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Token.Token t JOIN Token.AuthSession s ON s.SessionId=t.SessionId WHERE s.SubjectId=? AND s.AuthSessionStatusId=1", Integer.class, username)).isZero();
        assertThat(
                        sql.queryForObject(
                                "SELECT COUNT(*) FROM Token.AuthSession WHERE SubjectId=?",
                                Integer.class,
                                username))
                .isEqualTo(initial);
        assertThat(
                        sql.queryForObject(
                                "SELECT COUNT(*) FROM Token.AuthSession WHERE SubjectId=? AND"
                                        + " AuthSessionStatusId=1",
                                Integer.class,
                                username))
                .isZero();
    }

    @Test
    void supportedOldVersionSnapshotSuccessfullyUpgradesAndCreatesSession() throws Exception {
        var f = hosted("fix1-upgrade-ok-" + UUID.randomUUID().toString().substring(0, 8));
        sql.update("UPDATE Person.Profile SET TwoFactorEnabled=0 WHERE ProfileId=?", f.user().getId());
        // Valid legacy storage envelope: four-byte cipher index followed by opaque cipher bytes.
        // This is a post-authenticated snapshot fixture, not external v0 password verification.
        sql.update(
                "UPDATE Person.Credential SET Password=0x00000007AABBCCDDEEFF00112233445566778899,Version=0"
                        + " WHERE ProfileId=?",
                f.user().getId());
        String username = userService.getByGuid(f.user().getRowGuid()).orElseThrow().username();
        var snapshot =
                (CustomUserDetails)
                        userDetails.loadUserByUsername(username + System.lineSeparator() + "idp");
        assertThat(authenticateSnapshot(snapshot, "idp", f.password()).getStatus()).isEqualTo(302);
        var fresh =
                (CustomUserDetails)
                        userDetails.loadUserByUsername(username + System.lineSeparator() + "idp");
        assertThat(fresh.getUserAuthDetails().getVersion())
                .isEqualTo(PasswordEncoderFactory.currentVersion);
        assertThat(
                        passwordFactory.matches(
                                Base64.getEncoder().encodeToString(f.password().getBytes()),
                                fresh.getUserAuthDetails().getPassword(),
                                fresh.getUserAuthDetails().getVersion()))
                .isTrue();
    }

    @Test
    void f4UsernameRenameAndReuseCannotRebindBearerSsoOrPersistedGrant() throws Exception {
        String name="f4-reuse-"+UUID.randomUUID().toString().substring(0,8);
        var old=hosted(name);
        var oldBearer=token(old.user());
        String grantId=installGrant(old.access(),name,AuthorizationGrantType.AUTHORIZATION_CODE);
        var snapshot=grants.findById(grantId);
        var administrator=user(orgA,group(orgA,"f4-operator-"+UUID.randomUUID(),List.of("idp-admin-user:update")),"f4-admin-"+UUID.randomUUID());
        var response=call(token(administrator),"PUT","/api/v1/users/"+old.user().getRowGuid()+"/username",
                mapper.writeValueAsString(Map.of("branding","idp","intent","idp","metadata",Map.of(),"username",name+"-renamed")));
        assertThat(response.statusCode()).isEqualTo(204);
        var other=hosted(name,organization("F4 reused name tenant "+UUID.randomUUID()));
        assertThat(other.user().getRowGuid()).isNotEqualTo(old.user().getRowGuid());
        assertThat(call(oldBearer,"GET","/api/v1/portal/context",null).statusCode()).isEqualTo(401);
        assertThat(grants.findById(grantId)).isNull();
        assertThatThrownBy(()->grants.save(snapshot)).isInstanceOf(RuntimeException.class);
        String launch=old.launch().replace(old.user().getLoginId().toString(),other.user().getLoginId().toString());
        var continuation=old.browser().send("GET",launch,null,null);
        if(continuation.statusCode()==200) {
            assertThat(old.browser().send("GET",continuePath(continuation.body()),null,null).statusCode()).isEqualTo(403);
        } else assertThat(continuation.statusCode()).isEqualTo(403);
        assertThat(call(token(other.user()),"GET","/api/v1/portal/context",null).statusCode()).isEqualTo(200);
    }

    @Test
    void f4UnboundHumanAndUnprovenMachineFailClosedEvenWhenNameEqualsClientId() throws Exception {
        var f=hosted(app.getClientId());
        var token=tokenService.getByValue(f.access().value(),com.chellavignesh.authserver.enums.entity.TokenTypeEnum.ACCESS_TOKEN).orElseThrow();
        var claims=tokenService.getClaimsForToken(token);
        assertThat(claims.get("login_guid")).isEqualTo(f.user().getLoginId());
        sql.update("UPDATE Token.AuthSession SET LoginId=NULL WHERE SessionId=?",token.getSessionId());
        assertThat(tokenService.getClaimsForToken(token).get("active")).isEqualTo(false);
        // A real persisted client-credentials grant is the required positive machine proof.
        String machine=installGrant(f.access(),app.getClientId(),AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(tokenService.getClaimsForToken(token).get("active")).isEqualTo(true);
        assertThat(tokenService.getClaimsForToken(token).containsKey("login_guid")).isFalse();
        sql.update("UPDATE dbo.oauth2_authorization SET attributes=N'not-json' WHERE id=?",machine);
        assertThat(tokenService.getClaimsForToken(token).get("active")).isEqualTo(false);
    }

    @Test
    void f4ExactManagementSelectorPreservesSiblingsAndTargetOnlyHistory() throws Exception {
        var target=hosted("f4-exact-"+UUID.randomUUID().toString().substring(0,8));
        UUID sibling=siblingCredential(target,"f4-exact-sibling-"+UUID.randomUUID(),UUID.fromString("11111111-1111-1111-1111-111111111111"));
        var actor=user(orgA,group(orgA,"f4-exact-role-"+UUID.randomUUID(),List.of("idp-admin-user:read","idp-admin-user:update")),"f4-exact-admin-"+UUID.randomUUID());
        var access=token(actor);
        String path="/api/v1/users/"+target.user().getRowGuid();
        assertThat(call(access,"GET",path,null).statusCode()).isEqualTo(409);
        var detail=call(access,"GET",path+"?loginId="+sibling,null);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(sibling.toString().equalsIgnoreCase(mapper.readTree(detail.body()).path("loginId").asText())).isTrue();
        assertThat(call(access,"GET",path+"?loginId="+actor.getLoginId(),null).statusCode()).isEqualTo(404);
        String next="K9!qY"+UUID.randomUUID().toString().replace("-","")+"vT";
        byte[] siblingHistory=HexFormat.of().parseHex(passwordFactory.encode(Base64.getEncoder().encodeToString(next.getBytes())));
        sql.update("INSERT Person.CredentialAudit(CredentialId,Password,Version) SELECT Id,?,? FROM Person.Credential WHERE RowGuid=?",siblingHistory,PasswordEncoderFactory.currentVersion,sibling);
        byte[] before=sql.queryForObject("SELECT Password FROM Person.Credential WHERE RowGuid=?",byte[].class,sibling);
        int audit=sql.queryForObject("SELECT COUNT(*) FROM Person.CredentialAudit a JOIN Person.Credential c ON c.Id=a.CredentialId WHERE c.RowGuid=?",Integer.class,sibling);
        var body=Map.of("branding","idp","intent","idp","metadata",Map.of(),"password",Base64.getEncoder().encodeToString(next.getBytes()));
        assertThat(call(access,"PUT",path+"/password?loginId="+target.user().getLoginId(),mapper.writeValueAsString(body)).statusCode()).isEqualTo(204);
        assertThat(Arrays.equals(before,sql.queryForObject("SELECT Password FROM Person.Credential WHERE RowGuid=?",byte[].class,sibling))).isTrue();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.CredentialAudit a JOIN Person.Credential c ON c.Id=a.CredentialId WHERE c.RowGuid=?",Integer.class,sibling)).isEqualTo(audit);
        var changed=userService.getAuthDetailsByLoginId(target.user().getLoginId()).orElseThrow();
        assertThat(passwordFactory.matches(Base64.getEncoder().encodeToString(next.getBytes()),changed.getPassword(),changed.getVersion())).isTrue();
        assertThat(passwordFactory.matches(Base64.getEncoder().encodeToString(target.password().getBytes()),changed.getPassword(),changed.getVersion())).isFalse();
        assertThat(call(access,"PUT",path+"/password?loginId="+target.user().getLoginId(),mapper.writeValueAsString(body)).statusCode()).isEqualTo(400);
        sql.update("UPDATE Person.Profile SET Status=0 WHERE ProfileId=?",target.user().getId());
        assertThat(call(access,"GET",path+"?loginId="+sibling,null).statusCode()).isEqualTo(200);
    }

    @Test
    void f4ImportedReactivationTargetsOnlyTheSuppliedExistingLogin() throws Exception {
        var f=hosted("f4-reactivate-"+UUID.randomUUID().toString().substring(0,8));
        UUID source=UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID selected=siblingCredential(f,"f4-reactivate-selected-"+UUID.randomUUID(),source);
        int preservedId=sql.queryForObject("SELECT Id FROM Person.Credential WHERE RowGuid=?",Integer.class,f.user().getLoginId());
        byte[] preserved=sql.queryForObject("SELECT Password FROM Person.Credential WHERE Id=?",byte[].class,preservedId);
        int history=sql.queryForObject("SELECT COUNT(*) FROM Person.CredentialAudit WHERE CredentialId=?",Integer.class,preservedId);
        sql.update("UPDATE Person.Profile SET Status=0 WHERE ProfileId=?",f.user().getId());
        var dto=new com.chellavignesh.authserver.adminportal.user.dto.CreateUserProfileDto();
        var profile=new com.chellavignesh.authserver.adminportal.user.dto.CreateUserProfileDto.Profile();
        profile.setMemberId(f.user().getRowGuid());profile.setFirstName("Owned");profile.setLastName("Reactivated");
        profile.setPhoneNumber("+19999999998");profile.setEmail("reactivated@example.test");dto.setProfile(profile);
        var credential=new com.chellavignesh.authserver.adminportal.user.dto.CreateUserProfileDto.Credential();
        credential.setLoginId(selected);credential.setUsername("f4-reactivated-"+UUID.randomUUID());
        credential.setPasswordHash(passwordFactory.encode(Base64.getEncoder().encodeToString(("W9!qZ"+UUID.randomUUID()+"mV").getBytes())));
        dto.setCredential(credential);dto.setOrgId(orgA.getRowGuid());
        assertThatThrownBy(() -> users.reactivateAccountWithPassword(dto,f.user().getRowGuid(),source,PasswordEncoderFactory.currentVersion,1,null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        var actor=user(orgA,group(orgA,"f4-reactivate-role-"+UUID.randomUUID(),List.of("idp-admin-user:update")),"f4-reactivate-actor-"+UUID.randomUUID());
        for (UUID invalid : Arrays.asList(null,UUID.randomUUID(),actor.getLoginId())) {
            credential.setLoginId(invalid);
            assertThatThrownBy(() -> users.reactivateAccountWithPassword(dto,f.user().getRowGuid(),source,PasswordEncoderFactory.currentVersion,1,com.chellavignesh.authserver.adminportal.user.ReactivationAuditActor.human(actor.getRowGuid())))
                    .isInstanceOf(com.chellavignesh.authserver.adminportal.organization.role.RoleFailure.class);
            assertThat(sql.queryForObject("SELECT Status FROM Person.Profile WHERE ProfileId=?",Integer.class,f.user().getId())).isZero();
        }
        credential.setLoginId(selected);
        profile.setMemberId(UUID.randomUUID());
        assertThatThrownBy(() -> users.reactivateAccountWithPassword(dto,f.user().getRowGuid(),source,PasswordEncoderFactory.currentVersion,1,com.chellavignesh.authserver.adminportal.user.ReactivationAuditActor.human(actor.getRowGuid())))
                .isInstanceOf(com.chellavignesh.authserver.adminportal.organization.role.RoleFailure.class);
        profile.setMemberId(f.user().getRowGuid());
        users.reactivateAccountWithPassword(dto,f.user().getRowGuid(),source,PasswordEncoderFactory.currentVersion,1,com.chellavignesh.authserver.adminportal.user.ReactivationAuditActor.human(actor.getRowGuid()));
        assertThat(sql.queryForObject("SELECT Status FROM Person.Profile WHERE ProfileId=?",Integer.class,f.user().getId())).isEqualTo(1);
        assertThat(actor.getRowGuid().toString().equalsIgnoreCase(sql.queryForObject("SELECT ModifiedBy FROM Person.Credential WHERE RowGuid=?",String.class,selected))).isTrue();
        assertThat(actor.getRowGuid().toString().equalsIgnoreCase(sql.queryForObject("SELECT TOP 1 a.ModifiedBy FROM Person.CredentialAudit a JOIN Person.Credential c ON c.Id=a.CredentialId WHERE c.RowGuid=? ORDER BY a.CredentialAuditId DESC",String.class,selected))).isTrue();
        assertThat(Arrays.equals(preserved,sql.queryForObject("SELECT Password FROM Person.Credential WHERE Id=?",byte[].class,preservedId))).isTrue();
        assertThat(f.user().getLoginId().toString().equalsIgnoreCase(sql.queryForObject("SELECT CONVERT(VARCHAR(36),RowGuid) FROM Person.Credential WHERE Id=?",String.class,preservedId))).isTrue();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.CredentialAudit WHERE CredentialId=?",Integer.class,preservedId)).isEqualTo(history);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.Credential WHERE RowGuid=?",Integer.class,selected)).isEqualTo(1);
    }

    @Test
    void f4SchemaReactivationRejectsReplacementLoginBeforeWrites() throws Exception {
        var f=hosted("f4-schema-"+UUID.randomUUID().toString().substring(0,8));
        UUID source=UUID.randomUUID(); String brand="f4-schema-"+UUID.randomUUID();
        sql.update("INSERT dbo.ExternalSource(SourceId,SourceCode,ExternalTypeId) SELECT ?,?,ExternalTypeId FROM dbo.ExternalType WHERE ExternalTypeName='529'",source,brand);
        UUID selected=siblingCredential(f,"f4-schema-selected-"+UUID.randomUUID(),source);
        var actor=user(orgA,group(orgA,"f4-schema-role-"+UUID.randomUUID(),List.of("idp-admin-user:create","idp-admin-user:update")),"f4-schema-actor-"+UUID.randomUUID());
        var bearer=token(actor);
        sql.update("UPDATE Person.Profile SET Status=0 WHERE ProfileId=?",f.user().getId());
        byte[] before=sql.queryForObject("SELECT Password FROM Person.Credential WHERE RowGuid=?",byte[].class,selected);
        String schema="urn:ascensus:529:1.0:accountsync";
        var payload=Map.of("branding",brand,"intent","conversion","timestamp",Instant.now().toString(),"orgId",orgA.getRowGuid(),
                "credential",Map.of("loginId",UUID.randomUUID(),"username","f4-replace-"+UUID.randomUUID(),"passwordHash","001122","cIndex","0"),
                "profile",Map.of("memberId",f.user().getRowGuid(),"firstName","Owned","lastName","Reactivation","email","owned@example.test","phoneNumber","+19999999998","idpSyncDttm",Instant.now().toString()),
                "metadata",Map.of("idp",List.of(),"unite",List.of()),"syncFlag",false);
        var response=call(bearer,"POST","/api/v1/users/profile",mapper.writeValueAsString(Map.of("schema",schema,schema,payload)));
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat("CREDENTIAL_REQUIRES_REVIEW".equals(mapper.readTree(response.body()).path("code").asText())).isTrue();
        assertThat(sql.queryForObject("SELECT Status FROM Person.Profile WHERE ProfileId=?",Integer.class,f.user().getId())).isZero();
        assertThat(Arrays.equals(before,sql.queryForObject("SELECT Password FROM Person.Credential WHERE RowGuid=?",byte[].class,selected))).isTrue();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.Credential WHERE RowGuid=?",Integer.class,selected)).isEqualTo(1);
    }

    @Test
    void f4MachineReactivationUsesVerifiedClientAuditAndCurrentAuthority() throws Exception {
        var f=hosted("f4-machine-target-"+UUID.randomUUID().toString().substring(0,8));
        String username=userService.getByLoginId(f.user().getLoginId()).orElseThrow().username();
        var create=new com.chellavignesh.authserver.adminportal.application.dto.CreateApplicationDto();
        create.setName("F4 machine "+UUID.randomUUID());create.setDescription("Owned audit fixture");
        create.setType(com.chellavignesh.authserver.enums.entity.ApplicationTypeEnum.SERVER);
        create.setAuthMethod(com.chellavignesh.authserver.enums.entity.AuthFlowEnum.CLIENT_SECRET_JWT);
        create.setUsernameType(com.chellavignesh.authserver.enums.entity.UsernameTypeEnum.USERNAME);create.setAllowForgotUsername(false);
        var machine=applicationService.create(orgA.getId(),create);
        var secret=new com.chellavignesh.authserver.adminportal.credential.dto.CreateCredentialRequestDto();
        secret.setOrgId(orgA.getId());secret.setAppId(machine.getId());secret.setName("Owned F4 machine credential");secret.setDescription("Owned");
        secret.setAlgorithmEnum(com.chellavignesh.authserver.enums.entity.AlgorithmEnum.HS256);secret.setAuthFlow(com.chellavignesh.authserver.enums.entity.AuthFlowEnum.CLIENT_SECRET_JWT);
        secret.setCredentialStatus(com.chellavignesh.authserver.adminportal.credential.CredentialStatus.Active);secret.setExpireOn(Date.from(Instant.now().plusSeconds(86400)));
        machineCredentials.createSharedSecretCredential(secret);
        var bearer=token(machine,machine.getClientId(),"confidential-client");
        var principal=new com.chellavignesh.authserver.config.introspect.TokenIntrospector(tokenService).introspect(bearer.value());
        var authentication=new org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication(principal,
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,bearer.value(),Instant.now(),Instant.now().plusSeconds(600)),List.of());
        sql.update("UPDATE Person.Profile SET Status=0 WHERE ProfileId=?",f.user().getId());
        var transaction=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        assertThatThrownBy(()->transaction.execute(status->{new com.chellavignesh.authserver.security.ManagementMutationLock(sql).acquire();return managementPolicy.reactivationActor(authentication,orgA.getRowGuid(),f.user().getRowGuid());}))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        // Trusted token claims are derived by the real token service from assigned URL resources.
        for(String method:List.of("POST","PUT")) {
            int resource=sql.queryForObject("INSERT dbo.ResourceLibrary(Name,Description,Uri,AllowedMethod,Urn) OUTPUT INSERTED.ResourceLibraryId VALUES(?,?,?,?,?)",Integer.class,"F4 "+method,"Owned","http://user",method,"urn:f4:"+UUID.randomUUID());
            applications.assignResource(orgA.getId(),machine.getId(),resource);
        }
        var fresh=new com.chellavignesh.authserver.config.introspect.TokenIntrospector(tokenService).introspect(bearer.value());
        var allowed=new org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication(fresh,
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,bearer.value(),Instant.now(),Instant.now().plusSeconds(600)),List.of());
        var actor=transaction.execute(status->{new com.chellavignesh.authserver.security.ManagementMutationLock(sql).acquire();return managementPolicy.reactivationActor(allowed,orgA.getRowGuid(),f.user().getRowGuid());});
        assertThat(("client:"+machine.getRowGuid()).equals(actor.value())).isTrue();
        assertThatThrownBy(()->transaction.execute(status->{new com.chellavignesh.authserver.security.ManagementMutationLock(sql).acquire();return managementPolicy.reactivationActor(allowed,UUID.randomUUID(),f.user().getRowGuid());}))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        var dto=reactivationDto(f,username);
        users.reactivateAccountWithPassword(dto,f.user().getRowGuid(),UUID.fromString("11111111-1111-1111-1111-111111111111"),0,1,actor);
        assertThat(actor.value().equalsIgnoreCase(sql.queryForObject("SELECT ModifiedBy FROM Person.Credential WHERE RowGuid=?",String.class,f.user().getLoginId()))).isTrue();
        assertThat(actor.value().equalsIgnoreCase(sql.queryForObject("SELECT TOP 1 a.ModifiedBy FROM Person.CredentialAudit a JOIN Person.Credential c ON c.Id=a.CredentialId WHERE c.RowGuid=? ORDER BY a.CredentialAuditId DESC",String.class,f.user().getLoginId()))).isTrue();
    }

    @Test
    void f4AuthoritativeSourceSyncHasExplicitProvenanceAndRejectsInconsistentIdentity() throws Exception {
        var f=hosted("f4-source-"+UUID.randomUUID().toString().substring(0,8));
        String username=userService.getByLoginId(f.user().getLoginId()).orElseThrow().username();
        UUID sibling=siblingCredential(f,"f4-source-sibling-"+UUID.randomUUID(),UUID.fromString("11111111-1111-1111-1111-111111111111"));
        byte[] siblingBefore=sql.queryForObject("SELECT Password FROM Person.Credential WHERE RowGuid=?",byte[].class,sibling);
        sql.update("UPDATE Person.Profile SET Status=0 WHERE ProfileId=?",f.user().getId());
        var dto=reactivationDto(f,username); var source=externalSources.findBySourceCode("idp").orElseThrow();
        var mapped=com.chellavignesh.authserver.adminportal.user.mapper.UserDtoMapper.Instance.toCreateUserDto(dto);
        mapped.setOrgId(orgA.getId());
        var notifications=new com.chellavignesh.authserver.adminportal.user.CreateUserNotificationConfig();
        notifications.setFailNotificationSilently(true);
        assertThatThrownBy(()->userService.syncFromAuthoritativeSource("other",dto,mapped,Map.of(),source,notifications))
                .isInstanceOf(com.chellavignesh.authserver.adminportal.organization.role.RoleFailure.class);
        dto.setBranding("other");
        assertThatThrownBy(()->userService.syncFromAuthoritativeSource(username,dto,mapped,Map.of(),source,notifications))
                .isInstanceOf(com.chellavignesh.authserver.adminportal.organization.role.RoleFailure.class);
        dto.setBranding("idp"); dto.setOrgId(UUID.randomUUID());
        assertThatThrownBy(()->userService.syncFromAuthoritativeSource(username,dto,mapped,Map.of(),source,notifications))
                .isInstanceOf(com.chellavignesh.authserver.adminportal.organization.role.RoleFailure.class);
        dto.setOrgId(orgA.getRowGuid());
        userService.syncFromAuthoritativeSource(username,dto,mapped,Map.of(),source,notifications);
        assertThat(sql.queryForObject("SELECT Status FROM Person.Profile WHERE ProfileId=?",Integer.class,f.user().getId())).isEqualTo(1);
        assertThat(("source:"+source.getSourceId()).equalsIgnoreCase(sql.queryForObject("SELECT ModifiedBy FROM Person.Credential WHERE RowGuid=?",String.class,f.user().getLoginId()))).isTrue();
        assertThat(Arrays.equals(siblingBefore,sql.queryForObject("SELECT Password FROM Person.Credential WHERE RowGuid=?",byte[].class,sibling))).isTrue();
    }

    private com.chellavignesh.authserver.adminportal.user.dto.CreateUserProfileDto reactivationDto(HostedFixture f,String username) {
        var dto=new com.chellavignesh.authserver.adminportal.user.dto.CreateUserProfileDto();
        dto.setBranding("idp");dto.setIntent("");dto.setOrgId(orgA.getRowGuid());
        var profile=new com.chellavignesh.authserver.adminportal.user.dto.CreateUserProfileDto.Profile();
        profile.setMemberId(f.user().getRowGuid());profile.setFirstName("Owned");profile.setLastName("Sync");profile.setEmail("owned@example.test");profile.setPhoneNumber("+19999999998");profile.setIdpSyncDttm(Instant.now().toString());dto.setProfile(profile);
        var credential=new com.chellavignesh.authserver.adminportal.user.dto.CreateUserProfileDto.Credential();
        credential.setLoginId(f.user().getLoginId());credential.setUsername(username);credential.setPasswordHash("001122");credential.setcIndex("0");dto.setCredential(credential);
        return dto;
    }

    @Test
    void f4ManagementPasswordAndRenameAuditTheActualAdministrator() throws Exception {
        var target=hosted("f4-actor-target-"+UUID.randomUUID().toString().substring(0,8));
        var actor=user(orgA,group(orgA,"f4-actor-role-"+UUID.randomUUID(),List.of("idp-admin-user:update")),"f4-actor-"+UUID.randomUUID());
        var access=token(actor);
        String next="T8!rY"+UUID.randomUUID().toString().replace("-","")+"sM";
        var changed=call(access,"PUT","/api/v1/users/"+target.user().getRowGuid()+"/password",
                mapper.writeValueAsString(Map.of("branding","idp","intent","idp","metadata",Map.of(),"password",Base64.getEncoder().encodeToString(next.getBytes()))));
        assertThat(changed.statusCode()).isEqualTo(204);
        String audited=sql.queryForObject("SELECT TOP 1 a.ModifiedBy FROM Person.CredentialAudit a JOIN Person.Credential c ON c.Id=a.CredentialId WHERE c.RowGuid=? ORDER BY a.CredentialAuditId DESC",String.class,target.user().getLoginId());
        assertThat(actor.getRowGuid().toString().equalsIgnoreCase(audited)).isTrue();
        var renamed=call(access,"PUT","/api/v1/users/"+target.user().getRowGuid()+"/username",
                mapper.writeValueAsString(Map.of("branding","idp","intent","idp","metadata",Map.of(),"username","f4-renamed-"+UUID.randomUUID())));
        assertThat(renamed.statusCode()).isEqualTo(204);
        assertThat(actor.getRowGuid().toString().equalsIgnoreCase(sql.queryForObject("SELECT ModifiedBy FROM Person.Credential WHERE RowGuid=?",String.class,target.user().getLoginId()))).isTrue();
    }

    @Test
    void f4InactiveTargetPasswordRotationSucceedsAndKeepsSiblingsUntouched() throws Exception {
        var target=hosted("f4-inactive-target-"+UUID.randomUUID().toString().substring(0,8));
        var details=userService.getByLoginId(target.user().getLoginId()).orElseThrow();
        String siblingBrand="f4-other-"+UUID.randomUUID().toString().substring(0,8);
        UUID siblingSource=UUID.randomUUID();
        sql.update("INSERT dbo.ExternalSource(SourceId,SourceCode,ExternalTypeId) SELECT ?,?,ExternalTypeId FROM dbo.ExternalType WHERE ExternalTypeName='IDP'",siblingSource,siblingBrand);
        UUID sibling=siblingCredential(target,"f4-sibling-"+UUID.randomUUID(),siblingSource);
        byte[] siblingPassword=sql.queryForObject("SELECT Password FROM Person.Credential WHERE RowGuid=?",byte[].class,sibling);
        int siblingAudits=sql.queryForObject("SELECT COUNT(*) FROM Person.CredentialAudit a JOIN Person.Credential c ON c.Id=a.CredentialId WHERE c.RowGuid=?",Integer.class,sibling);
        sql.update("UPDATE Person.Profile SET Status=0 WHERE ProfileId=?",target.user().getId());
        var actor=user(orgA,group(orgA,"f4-inactive-role-"+UUID.randomUUID(),List.of("idp-admin-user:update")),"f4-inactive-actor-"+UUID.randomUUID());
        String next="Y7!uN"+UUID.randomUUID().toString().replace("-","")+"pW";
        var changed=call(token(actor),"PUT","/api/v1/users/"+target.user().getRowGuid()+"/password?loginId="+details.loginId(),
                mapper.writeValueAsString(Map.of("branding","idp","intent","idp","metadata",Map.of(),"password",Base64.getEncoder().encodeToString(next.getBytes()))));
        assertThat(changed.statusCode()).isEqualTo(204);
        String audited=sql.queryForObject("SELECT TOP 1 a.ModifiedBy FROM Person.CredentialAudit a JOIN Person.Credential c ON c.Id=a.CredentialId WHERE c.RowGuid=? ORDER BY a.CredentialAuditId DESC",String.class,details.loginId());
        assertThat(audited.equalsIgnoreCase(actor.getRowGuid().toString())).isTrue();
        var rotated=userService.getUserAuthDetailsByUsernameAndExternalSourceCode(details.username(),"idp").orElseThrow();
        assertThat(passwordFactory.matches(Base64.getEncoder().encodeToString(next.getBytes()),rotated.getPassword(),rotated.getVersion())).isTrue();
        assertThat(Arrays.equals(siblingPassword,sql.queryForObject("SELECT Password FROM Person.Credential WHERE RowGuid=?",byte[].class,sibling))).isTrue();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.CredentialAudit a JOIN Person.Credential c ON c.Id=a.CredentialId WHERE c.RowGuid=?",Integer.class,sibling)).isEqualTo(siblingAudits);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Token.AuthSession WHERE SubjectId=? AND Branding='idp' AND AuthSessionStatusId=?",Integer.class,details.username(),AuthSessionStatusEnum.ACTIVE.getValue())).isZero();
        assertThat(call(target.access(),"GET","/api/v1/portal/context",null).statusCode()).isEqualTo(401);
    }

    @Test
    void f4ManagementIdentityCarriesOrganizationForDownstreamSyncPayload() throws Exception {
        var target=hosted("f4-sync-org-"+UUID.randomUUID().toString().substring(0,8));
        var active=userService.getManagementCredentialByLoginId(target.user().getLoginId()).orElseThrow().toUserDetails();
        assertThat(active.profileOrgId()).isEqualTo(orgA.getRowGuid());
        assertThat(active.orgId()).isEqualTo(orgA.getRowGuid());
        sql.update("UPDATE Person.Profile SET Status=0 WHERE ProfileId=?",target.user().getId());
        var inactive=userService.getManagementCredentialByLoginId(target.user().getLoginId()).orElseThrow().toUserDetails();
        assertThat(inactive.profileOrgId()).isEqualTo(orgA.getRowGuid());
        assertThat(inactive.orgId()).isEqualTo(orgA.getRowGuid());
    }

    @Test
    void f4InactiveTargetUsernameRenameSucceeds() throws Exception {
        var target=hosted("f4-inactive-rename-"+UUID.randomUUID().toString().substring(0,8));
        var details=userService.getByLoginId(target.user().getLoginId()).orElseThrow();
        sql.update("UPDATE Person.Profile SET Status=0 WHERE ProfileId=?",target.user().getId());
        var actor=user(orgA,group(orgA,"f4-inactive-rename-role-"+UUID.randomUUID(),List.of("idp-admin-user:update")),"f4-inactive-rename-actor-"+UUID.randomUUID());
        String renamed="f4-inactive-renamed-"+UUID.randomUUID();
        var changed=call(token(actor),"PUT","/api/v1/users/"+target.user().getRowGuid()+"/username?loginId="+details.loginId(),
                mapper.writeValueAsString(Map.of("branding","idp","intent","idp","metadata",Map.of(),"username",renamed)));
        assertThat(changed.statusCode()).isEqualTo(204);
        assertThat(sql.queryForObject("SELECT UserName FROM Person.Credential WHERE RowGuid=?",String.class,details.loginId())).isEqualTo(renamed);
        assertThat(sql.queryForObject("SELECT ModifiedBy FROM Person.Credential WHERE RowGuid=?",String.class,details.loginId())).isEqualTo(actor.getRowGuid().toString());
    }

    @Test
    void f4InactiveTargetCannotEstablishAnInteractiveSession() throws Exception {
        var target=hosted("f4-inactive-auth-"+UUID.randomUUID().toString().substring(0,8));
        sql.update("UPDATE Person.Profile SET TwoFactorEnabled=0 WHERE ProfileId=?",target.user().getId());
        var details=userService.getByLoginId(target.user().getLoginId()).orElseThrow();
        var snapshot=(CustomUserDetails)userDetails.loadUserByUsername(details.username()+System.lineSeparator()+"idp");
        sql.update("UPDATE Person.Profile SET Status=0 WHERE ProfileId=?",target.user().getId());
        assertThat(authenticateSnapshot(snapshot,"idp",target.password()).getStatus()).isEqualTo(401);
    }

    @Test
    void f4LegacyPasswordWriterRejectsAmbiguousProfileBeforeAnyCredentialWrite() throws Exception {
        var f = hosted("f4-ambiguous-" + UUID.randomUUID().toString().substring(0, 8));
        UUID sibling = siblingCredential(f, "f4-sibling-" + UUID.randomUUID(),
                UUID.fromString("11111111-1111-1111-1111-111111111111"));
        byte[] before = sql.queryForObject("SELECT Password FROM Person.Credential WHERE RowGuid=?",
                byte[].class, sibling);
        String next = "N9!qZ" + UUID.randomUUID().toString().replace("-", "") + "vR";
        var administrator=user(orgA,group(orgA,"f4-ambiguous-role-"+UUID.randomUUID(),List.of("idp-admin-user:update")),"f4-ambiguous-actor-"+UUID.randomUUID());
        var denied=call(token(administrator),"PUT","/api/v1/users/"+f.user().getRowGuid()+"/password",
                mapper.writeValueAsString(Map.of("branding","idp","intent","idp","metadata",Map.of(),"password",Base64.getEncoder().encodeToString(next.getBytes()))));
        assertThat(denied.statusCode()).isEqualTo(409);
        assertThat(Arrays.equals(before, sql.queryForObject(
                "SELECT Password FROM Person.Credential WHERE RowGuid=?", byte[].class, sibling))).isTrue();
    }

    @Test
    void f4AuthenticatedOldVersionUpgradePreservesSameSourceSiblingCredentialAndAudit() throws Exception {
        var f = hosted("f4-upgrade-" + UUID.randomUUID().toString().substring(0, 8));
        sql.update("UPDATE Person.Profile SET TwoFactorEnabled=0 WHERE ProfileId=?", f.user().getId());
        UUID sibling = siblingCredential(f, "f4-other-" + UUID.randomUUID(),
                UUID.fromString("11111111-1111-1111-1111-111111111111"));
        byte[] before = sql.queryForObject("SELECT Password FROM Person.Credential WHERE RowGuid=?",
                byte[].class, sibling);
        int audits = sql.queryForObject("SELECT COUNT(*) FROM Person.CredentialAudit a JOIN Person.Credential c ON c.Id=a.CredentialId WHERE c.RowGuid=?", Integer.class, sibling);
        // Post-authenticated v0 envelope only; no external legacy-provider verification claim.
        sql.update("UPDATE Person.Credential SET Password=0x00000007AABBCCDDEEFF00112233445566778899,Version=0 WHERE RowGuid=?", f.user().getLoginId());
        String name = userService.getByLoginId(f.user().getLoginId()).orElseThrow().username();
        var snapshot = (CustomUserDetails) userDetails.loadUserByUsername(name + System.lineSeparator() + "idp");
        assertThat(authenticateSnapshot(snapshot, "idp", f.password()).getStatus()).isEqualTo(302);
        assertThat(Arrays.equals(before, sql.queryForObject(
                "SELECT Password FROM Person.Credential WHERE RowGuid=?", byte[].class, sibling))).isTrue();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM Person.CredentialAudit a JOIN Person.Credential c ON c.Id=a.CredentialId WHERE c.RowGuid=?", Integer.class, sibling)).isEqualTo(audits);
        var fresh = (CustomUserDetails) userDetails.loadUserByUsername(name + System.lineSeparator() + "idp");
        assertThat(fresh.getUserAuthDetails().getVersion()).isEqualTo(PasswordEncoderFactory.currentVersion);
        assertThat(passwordFactory.matches(Base64.getEncoder().encodeToString(f.password().getBytes()),
                fresh.getUserAuthDetails().getPassword(), fresh.getUserAuthDetails().getVersion())).isTrue();
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
                "UPDATE Token.AuthSession SET Branding=?,LoginId=(SELECT c.RowGuid FROM Person.Credential c JOIN dbo.ExternalSource e ON e.SourceId=c.ExternalId WHERE c.UserName=Token.AuthSession.SubjectId AND e.SourceCode=?) WHERE SessionId=?", brand, brand, otherSession);
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

    @Test
    void f4StatusChangeRevokesEveryProfileLoginAndReactivationStartsFresh() throws Exception {
        var f = hosted("f4-status-" + UUID.randomUUID().toString().substring(0, 8));
        String siblingUsername = "f4-status-sibling-" + UUID.randomUUID();
        UUID sibling =
                siblingCredential(
                        f,
                        siblingUsername,
                        UUID.fromString("11111111-1111-1111-1111-111111111111"));
        var siblingAccess = token(app, siblingUsername, "openid metadata idp-admin");
        String primarySession = sessionIdOf(f.access().value());
        String siblingSession = sessionIdOf(siblingAccess.value());
        assertThat(sessionStatus(primarySession))
                .isEqualTo(AuthSessionStatusEnum.ACTIVE.getValue());
        assertThat(sessionStatus(siblingSession))
                .isEqualTo(AuthSessionStatusEnum.ACTIVE.getValue());
        assertThat(activeFor(f.access())).isEqualTo(true);
        assertThat(activeFor(siblingAccess)).isEqualTo(true);

        reset(revocations);
        userService.updateUserStatusByGuid(UserStatus.Inactive, f.user().getRowGuid()).orElseThrow();

        verify(revocations, times(1)).revokeAllForProfile(f.user().getRowGuid());
        assertThat(sessionStatus(primarySession))
                .isEqualTo(AuthSessionStatusEnum.INACTIVE.getValue());
        assertThat(sessionStatus(siblingSession))
                .isEqualTo(AuthSessionStatusEnum.INACTIVE.getValue());
        assertThat(activeFor(f.access())).isEqualTo(false);
        assertThat(activeFor(siblingAccess)).isEqualTo(false);
        assertThat(sql.queryForObject("SELECT Status FROM Person.Profile WHERE ProfileId=?", Integer.class, f.user().getId()))
                .isZero();

        reset(revocations);
        userService.updateUserStatusByGuid(UserStatus.Active, f.user().getRowGuid()).orElseThrow();

        verify(revocations, never()).revokeAllForProfile(any());
        assertThat(sessionStatus(primarySession))
                .isEqualTo(AuthSessionStatusEnum.INACTIVE.getValue());
        assertThat(sessionStatus(siblingSession))
                .isEqualTo(AuthSessionStatusEnum.INACTIVE.getValue());
        assertThat(activeFor(f.access())).isEqualTo(false);
        assertThat(activeFor(siblingAccess)).isEqualTo(false);
        assertThat(sql.queryForObject("SELECT Status FROM Person.Profile WHERE ProfileId=?", Integer.class, f.user().getId()))
                .isEqualTo(1);
        assertThat(sibling).isNotNull();
    }

    @Test
    void f4LockedCredentialDeactivatesExistingHumanSession() throws Exception {
        var f = hosted("f4-lock-" + UUID.randomUUID().toString().substring(0, 8));
        assertThat(activeFor(f.access())).isEqualTo(true);

        sql.update("UPDATE Person.Credential SET CredentialLocked=1 WHERE RowGuid=?", f.user().getLoginId());
        assertThat(activeFor(f.access())).isEqualTo(false);

        // The lock is the only difference: clearing it restores the same, already-issued session.
        sql.update("UPDATE Person.Credential SET CredentialLocked=0 WHERE RowGuid=?", f.user().getLoginId());
        assertThat(activeFor(f.access())).isEqualTo(true);
    }

    String sessionIdOf(String tokenValue) {
        return sql.queryForObject(
                "SELECT CONVERT(VARCHAR(36),SessionId) FROM Token.Token WHERE Data=?",
                String.class,
                tokenValue);
    }

    int sessionStatus(String sessionId) {
        return sql.queryForObject(
                "SELECT AuthSessionStatusId FROM Token.AuthSession WHERE SessionId=?",
                Integer.class,
                sessionId);
    }

    Object activeFor(Fixture access) {
        var token =
                tokenService
                        .getByValue(
                                access.value(),
                                com.chellavignesh.authserver.enums.entity.TokenTypeEnum
                                        .ACCESS_TOKEN)
                        .orElseThrow();
        return tokenService.getClaimsForToken(token).get("active");
    }
}
