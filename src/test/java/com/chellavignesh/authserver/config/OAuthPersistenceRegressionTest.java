package com.chellavignesh.authserver.config;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.authcode.AuthCodeService;
import com.chellavignesh.authserver.enums.entity.AuthSessionStatusEnum;
import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import com.chellavignesh.authserver.session.AuthSessionService;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.token.SigningKeyGenerator;
import com.chellavignesh.authserver.token.TokenService;
import com.chellavignesh.authserver.token.dto.CreateTokenDto;
import com.chellavignesh.authserver.token.entity.Token;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.security.Principal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OAuthPersistenceRegressionTest {
    final ApplicationService applications = mock(ApplicationService.class);
    final TokenService tokens = mock(TokenService.class);
    final AuthSessionService sessions = mock(AuthSessionService.class);
    final AuthCodeService codes = mock(AuthCodeService.class);
    final SigningKeyGenerator keys = mock(SigningKeyGenerator.class);
    final com.chellavignesh.authserver.session.CredentialAuthenticationLock credentialLock = mock(com.chellavignesh.authserver.session.CredentialAuthenticationLock.class);
    final InMemoryOAuth2AuthorizationService state = new InMemoryOAuth2AuthorizationService();
    final JdbcOAuth2AuthorizationService service = new JdbcOAuth2AuthorizationService(tokens, applications,
            sessions, codes, null, keys, null, credentialLock, state);
    final UUID sessionId = UUID.randomUUID();
    final Instant now = Instant.now();
    final RegisteredClient client = RegisteredClient.withId("client").clientId("client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://client.example/callback").build();

    void fixture() {
        Application app = new Application(); app.setId(1);
        when(applications.getByClientId("client")).thenReturn(Optional.of(app));
        AuthSession session = new AuthSession(); session.setSessionId(sessionId);
        session.setAuthSessionStatus(AuthSessionStatusEnum.ACTIVE);
        when(sessions.getBySessionId(sessionId)).thenReturn(Optional.of(session));
    }

    OAuth2Authorization pending() {
        var request = OAuth2AuthorizationRequest.authorizationCode().clientId("client")
                .authorizationUri("https://issuer.example/oauth2/authorize")
                .redirectUri("https://client.example/callback").scopes(Set.of("openid", "profile"))
                .additionalParameters(p -> { p.put("code_challenge", "proof"); p.put("code_challenge_method", "S256"); }).build();
        return OAuth2Authorization.withRegisteredClient(client).id("grant")
                .principalName("alice").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid", "profile"))
                .attribute(JdbcOAuth2AuthorizationService.SESSION_ID, sessionId.toString())
                .attribute(OAuth2AuthorizationRequest.class.getName(), request)
                .attribute(Principal.class.getName(), UsernamePasswordAuthenticationToken.authenticated(
                        "alice", "secret", List.of(new SimpleGrantedAuthority("ROLE_USER"))))
                .token(new OAuth2AuthorizationCode("code", now, now.plusSeconds(60))).build();
    }

    OAuth2Authorization exchanged(OAuth2Authorization pending, String access, String refresh) {
        return OAuth2Authorization.from(pending)
                .token(pending.getToken(OAuth2AuthorizationCode.class).getToken(),
                        metadata -> metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true))
                .accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, access, now, now.plusSeconds(300), Set.of("openid")))
                .refreshToken(new OAuth2RefreshToken(refresh, now, now.plusSeconds(600))).build();
    }

    @Test
    void missingApplicationFailsIssuanceInsteadOfReportingSuccess() {
        when(applications.getByClientId("client")).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.save(pending()));
        assertNull(state.findById("grant"));
    }

    @Test
    void initialGrantCannotReplaceTheAuthenticatedSessionsBrand() {
        fixture();
        AuthSession session = sessions.getBySessionId(sessionId).orElseThrow();
        session.setApplicationId(1); session.setSubjectId("alice"); session.setBranding("idp");
        var browser = new org.springframework.mock.web.MockHttpSession();
        browser.setAttribute(ApplicationConstants.AUTH_SESSION_ID, sessionId);
        browser.setAttribute(ApplicationConstants.AUTHENTICATED_BRANDING, "idp");
        browser.setAttribute(ApplicationConstants.BRANDING_INFO, "other");
        var issuing = new JdbcOAuth2AuthorizationService(tokens, applications, sessions, codes, null, keys, browser, credentialLock, state);
        assertThrows(OAuth2AuthenticationException.class, () -> issuing.save(pending()));
        verifyNoInteractions(codes, tokens);
        assertNull(state.findById("grant"));
    }

    @Test
    void codeExchangePersistsRefreshAndRetainsOriginalGrantProofScopesAndPrincipal() throws Exception {
        fixture(); var original = pending(); state.save(original);
        List<CreateTokenDto> persisted = new ArrayList<>();
        when(tokens.create(any(), any(), any(), any())).thenAnswer(invocation -> {
            persisted.add(invocation.getArgument(0)); return new Token();
        });
        service.save(exchanged(original, "access", "refresh"));
        var restored = service.findByToken("refresh", OAuth2TokenType.REFRESH_TOKEN);
        assertEquals(AuthorizationGrantType.AUTHORIZATION_CODE, restored.getAuthorizationGrantType());
        assertEquals(Set.of("openid", "profile"), restored.getAuthorizedScopes());
        assertEquals(Set.of("openid"), restored.getAccessToken().getToken().getScopes());
        assertEquals("refresh", restored.getRefreshToken().getToken().getTokenValue());
        assertTrue(restored.getToken(OAuth2AuthorizationCode.class).isInvalidated());
        OAuth2AuthorizationRequest request = restored.getAttribute(OAuth2AuthorizationRequest.class.getName());
        assertEquals("proof", request.getAdditionalParameters().get("code_challenge"));
        var principal = (UsernamePasswordAuthenticationToken) restored.getAttribute(Principal.class.getName());
        assertEquals("alice", principal.getName()); assertNull(principal.getCredentials());
        assertTrue(principal.isAuthenticated());
        assertEquals(List.of(TokenTypeEnum.ACCESS_TOKEN, TokenTypeEnum.REFRESH_TOKEN), persisted.stream().map(CreateTokenDto::getTokenType).toList());
    }

    @Test
    void failedAtomicCodeConsumptionPreventsNewGrantState() {
        fixture(); var original = pending(); state.save(original);
        doThrow(new DataIntegrityViolationException("already consumed")).when(codes).setConsumedOn("code");
        assertThrows(RuntimeException.class, () -> service.save(exchanged(original, "access", "refresh")));
        assertNull(state.findByToken("access", OAuth2TokenType.ACCESS_TOKEN));
        verifyNoInteractions(tokens);
    }

    @Test
    void refreshRotationConsumesOldRefreshAndKeepsSession() throws Exception {
        fixture(); state.save(exchanged(pending(), "access", "refresh"));
        Token old = new Token(); old.setId(5); old.setSessionId(sessionId); old.setExpiration(Date.from(now.plusSeconds(600)));
        when(tokens.getByValue("refresh", TokenTypeEnum.REFRESH_TOKEN)).thenReturn(Optional.of(old));
        var presented = service.findByToken("refresh", OAuth2TokenType.REFRESH_TOKEN);
        service.save(exchanged(presented, "access2", "refresh2"));
        verify(tokens).consume(5);
        verify(sessions, never()).createSession(any(Optional.class), any());
        assertNull(service.findByToken("refresh", OAuth2TokenType.REFRESH_TOKEN));
        assertEquals(sessionId.toString(), service.findByToken("refresh2", OAuth2TokenType.REFRESH_TOKEN)
                .getAttribute(JdbcOAuth2AuthorizationService.SESSION_ID));
    }

    @Test
    void configuredRefreshReusePreservesCurrentRefreshWithoutConsumingOrReinserting() throws Exception {
        fixture(); state.save(exchanged(pending(), "access", "refresh"));
        Token old = new Token(); old.setId(5); old.setSessionId(sessionId); old.setExpiration(Date.from(now.plusSeconds(600)));
        when(tokens.getByValue("refresh", TokenTypeEnum.REFRESH_TOKEN)).thenReturn(Optional.of(old));
        service.save(exchanged(service.findByToken("refresh", OAuth2TokenType.REFRESH_TOKEN), "access2", "refresh"));
        verify(tokens, never()).consume(any());
        var captor = org.mockito.ArgumentCaptor.forClass(CreateTokenDto.class);
        verify(tokens).create(captor.capture(), any(), any(), any());
        assertEquals(TokenTypeEnum.ACCESS_TOKEN, captor.getValue().getTokenType());
        assertNotNull(service.findByToken("refresh", OAuth2TokenType.REFRESH_TOKEN));
    }

    @Test
    void inactiveSessionCannotReconstructAuthorization() {
        fixture(); state.save(pending());
        when(sessions.getBySessionId(sessionId)).thenReturn(Optional.empty());
        assertNull(service.findByToken("code", new OAuth2TokenType("code")));
    }
    @Test
    void springJdbcSerializationRestoresIdentityRequestAndProofWithoutCredentials() throws Exception {
        fixture(); var original = pending(); state.save(original);
        service.save(exchanged(original, "access", "refresh"));
        var saved = state.findById("grant");
        class Mapper extends org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper {
            com.fasterxml.jackson.databind.ObjectMapper mapper() { return getObjectMapper(); }
        }
        var mapper = new Mapper();
        String json = mapper.mapper().writeValueAsString(saved.getAttributes());
        Map<String, Object> attributes = mapper.mapper().readValue(json,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        var principal = (UsernamePasswordAuthenticationToken) attributes.get(Principal.class.getName());
        assertEquals("alice", principal.getPrincipal());
        assertNull(principal.getCredentials());
        assertEquals("ROLE_USER", principal.getAuthorities().iterator().next().getAuthority());
        var request = (OAuth2AuthorizationRequest) attributes.get(OAuth2AuthorizationRequest.class.getName());
        assertEquals("https://client.example/callback", request.getRedirectUri());
        assertEquals("proof", request.getAdditionalParameters().get("code_challenge"));
    }

    @Test
    void replayedCodeCannotReplaceAnAlreadyIssuedAccessToken() throws Exception {
        fixture(); var original = pending(); state.save(original);
        service.save(exchanged(original, "access", "refresh"));
        assertThrows(OAuth2AuthenticationException.class,
                () -> service.save(exchanged(original, "replay-access", "replay-refresh")));
        assertNull(state.findByToken("replay-access", OAuth2TokenType.ACCESS_TOKEN));
        assertNotNull(state.findByToken("access", OAuth2TokenType.ACCESS_TOKEN));
    }

    @Test
    void tokenInsertFailureDoesNotPublishUpdatedAuthorizationState() throws Exception {
        fixture(); var original = pending(); state.save(original);
        when(tokens.create(any(), any(), any(), any())).thenThrow(
                new com.chellavignesh.authserver.token.exception.TokenCreationFailedException("insert failed"));
        assertThrows(IllegalStateException.class, () -> service.save(exchanged(original, "access", "refresh")));
        assertNull(state.findByToken("access", OAuth2TokenType.ACCESS_TOKEN));
    }

}
