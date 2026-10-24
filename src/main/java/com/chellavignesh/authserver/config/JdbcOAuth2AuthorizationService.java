package com.chellavignesh.authserver.config;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.authcode.AuthCodeService;
import com.chellavignesh.authserver.authcode.dto.CreateAuthCodeDto;
import com.chellavignesh.authserver.enums.entity.AuthSessionStatusEnum;
import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import com.chellavignesh.authserver.pkce.PkceService;
import com.chellavignesh.authserver.pkce.dto.CreatePkceDto;
import com.chellavignesh.authserver.session.AuthSessionService;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.token.SigningKeyGenerator;
import com.chellavignesh.authserver.token.TokenService;
import com.chellavignesh.authserver.token.dto.CreateTokenDto;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.security.Principal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Keeps Spring's complete grant state and the application's token API in one transaction. */
@Service
public class JdbcOAuth2AuthorizationService implements OAuth2AuthorizationService {
    public static final String SESSION_ID = JdbcOAuth2AuthorizationService.class.getName() + ".sessionId";
    private static final String PRESENTED_REFRESH = JdbcOAuth2AuthorizationService.class.getName() + ".refresh";
    private final OAuth2AuthorizationService authorizations;
    private final TokenService tokens;
    private final ApplicationService applications;
    private final AuthSessionService sessions;
    private final AuthCodeService codes;
    private final PkceService pkce;
    private final SigningKeyGenerator signingKeys;
    private final HttpSession httpSession;
    private final com.chellavignesh.authserver.session.CredentialAuthenticationLock credentialLock;

    @Autowired
    public JdbcOAuth2AuthorizationService(TokenService tokens, ApplicationService applications,
            AuthSessionService sessions, AuthCodeService codes, PkceService pkce,
            SigningKeyGenerator signingKeys, NamedParameterJdbcTemplate jdbc,
            RegisteredClientRepository clients, HttpSession httpSession, com.chellavignesh.authserver.session.CredentialAuthenticationLock credentialLock) {
        this(tokens, applications, sessions, codes, pkce, signingKeys, httpSession, credentialLock,
                new org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService(
                        jdbc.getJdbcTemplate(), clients));
    }

    JdbcOAuth2AuthorizationService(TokenService tokens, ApplicationService applications,
            AuthSessionService sessions, AuthCodeService codes, PkceService pkce,
            SigningKeyGenerator signingKeys, HttpSession httpSession, com.chellavignesh.authserver.session.CredentialAuthenticationLock credentialLock, OAuth2AuthorizationService authorizations) {
        this.tokens = tokens;
        this.applications = applications;
        this.sessions = sessions;
        this.codes = codes;
        this.pkce = pkce;
        this.signingKeys = signingKeys;
        this.httpSession = httpSession;
        this.credentialLock = credentialLock;
        this.authorizations = authorizations;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(OAuth2Authorization authorization) {
        Application app = applications.getByClientId(authorization.getRegisteredClientId())
                .orElseThrow(() -> new IllegalStateException("Registered application not found"));
        OAuth2Authorization previous;
        if (!AuthorizationGrantType.CLIENT_CREDENTIALS.equals(authorization.getAuthorizationGrantType())) {
            String bound = authorization.getAttribute(SESSION_ID);
            Object pending = bound != null ? bound : httpSession == null ? null : httpSession.getAttribute(ApplicationConstants.AUTH_SESSION_ID);
            if (pending == null) throw invalidGrant();
            AuthSession candidate = sessions.getBySessionId(UUID.fromString(pending.toString())).orElseThrow(this::invalidGrant);
            credentialLock.lockForHumanSession(candidate);
            // Never recreate a previously persisted human grant after revocation deleted it.
            previous = authorizations.findById(authorization.getId());
            if (bound != null && previous == null) throw invalidGrant();
            AuthSession fresh = sessions.getBySessionId(candidate.getSessionId()).orElseThrow(this::invalidGrant);
            if (fresh.getAuthSessionStatus() != AuthSessionStatusEnum.ACTIVE) throw invalidGrant();
        } else {
            previous = authorizations.findById(authorization.getId());
        }
        UUID sessionId;
        try {
            if (previous == null) {
                sessionId = createInitialState(app, authorization);
            } else {
                sessionId = sessionId(previous);
                AuthSession session = sessions.getBySessionId(sessionId).orElseThrow(this::invalidGrant);
                if (session.getAuthSessionStatus() != AuthSessionStatusEnum.ACTIVE) {
                    throw invalidGrant();
                }
            }

            if (changed(authorization.getAccessToken(), previous == null ? null : previous.getAccessToken())) {
                if (previous != null) {
                    String presentedRefresh = authorization.getAttribute(PRESENTED_REFRESH);
                    if (presentedRefresh != null) {
                        // SQL conditional update is the serialization point for concurrent refresh requests.
                        var refresh = tokens.getByValue(presentedRefresh, TokenTypeEnum.REFRESH_TOKEN)
                                .filter(t -> sessionId.equals(t.getSessionId())).orElseThrow(this::invalidGrant);
                        if (refresh.getConsumedOn() != null || refresh.getExpiration() == null
                                || !refresh.getExpiration().toInstant().isAfter(java.time.Instant.now())) {
                            throw invalidGrant();
                        }
                        if (changed(authorization.getRefreshToken(), previous.getRefreshToken())) {
                            tokens.consume(refresh.getId());
                        }
                    } else {
                        var code = previous.getToken(OAuth2AuthorizationCode.class);
                        if (code == null || !code.isActive() || previous.getAccessToken() != null) {
                            throw invalidGrant();
                        }
                        // Updates code and its custom Token row only when unconsumed and unexpired.
                        codes.setConsumedOn(code.getToken().getTokenValue());
                    }
                }
                SecretKey signingKey = signingKeys.generateKey();
                persistToken(app, sessionId, authorization.getPrincipalName(), TokenTypeEnum.ACCESS_TOKEN,
                        authorization.getAccessToken().getToken(), signingKey);
                if (changed(authorization.getRefreshToken(), previous == null ? null : previous.getRefreshToken())) {
                    persistToken(app, sessionId, authorization.getPrincipalName(), TokenTypeEnum.REFRESH_TOKEN,
                            authorization.getRefreshToken().getToken(), signingKey);
                }
            }
            if (authorization.getAccessToken() != null && authorization.getAccessToken().isInvalidated()) {
                sessions.setSessionInactive(sessionId);
            }
            OAuth2Authorization.Builder state = OAuth2Authorization.from(authorization)
                    .attribute(SESSION_ID, sessionId.toString());
            state.attributes(attributes -> attributes.remove(PRESENTED_REFRESH));
            normalizePrincipal(authorization, state);
            authorizations.save(state.build());
        } catch (OAuth2AuthenticationException e) {
            throw e;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // A browser continuation/session represents a single authorization grant.
            // Replaying it must roll back the partial grant and return an OAuth error.
            throw invalidGrant();
        } catch (Exception e) {
            throw new IllegalStateException("Could not persist OAuth authorization", e);
        }
    }

    private UUID createInitialState(Application app, OAuth2Authorization authorization) throws Exception {
        var code = authorization.getToken(OAuth2AuthorizationCode.class);
        if (code == null) {
            return sessions.createSession(Optional.of(app), authorization).getSessionId();
        }
        Object pendingSession = httpSession.getAttribute(ApplicationConstants.AUTH_SESSION_ID);
        if (pendingSession == null) {
            throw invalidGrant();
        }
        UUID sessionId = UUID.fromString(pendingSession.toString());
        AuthSession session = sessions.getBySessionId(sessionId).orElseThrow(this::invalidGrant);
        if (session.getAuthSessionStatus() != AuthSessionStatusEnum.ACTIVE
                || !Objects.equals(session.getApplicationId(), app.getId())
                || !Objects.equals(session.getSubjectId(), authorization.getPrincipalName())
                || session.getBranding() == null
                || !Objects.equals(session.getBranding(), httpSession.getAttribute(ApplicationConstants.AUTHENTICATED_BRANDING))
                || !Objects.equals(session.getBranding(), httpSession.getAttribute(ApplicationConstants.BRANDING_INFO))) {
            throw invalidGrant();
        }
        OAuth2AuthorizationRequest request = authorization.getAttribute(OAuth2AuthorizationRequest.class.getName());
        if (request == null) {
            throw new IllegalStateException("Authorization request is missing");
        }
        CreateAuthCodeDto dto = new CreateAuthCodeDto();
        dto.setApplicationId(app.getId());
        dto.setSessionId(sessionId);
        dto.setData(code.getToken().getTokenValue());
        dto.setExpiration(code.getToken().getExpiresAt());
        codes.create(dto);
        Object challenge = request.getAdditionalParameters().get("code_challenge");
        if (challenge != null) {
            CreatePkceDto proof = new CreatePkceDto();
            proof.setApplicationId(app.getId());
            proof.setSessionId(sessionId);
            proof.setData(challenge.toString());
            proof.setAlgorithm(Objects.toString(request.getAdditionalParameters().get("code_challenge_method"), "plain"));
            proof.setRedirectUri(request.getRedirectUri());
            pkce.create(proof);
        }
        persistToken(app, sessionId, authorization.getPrincipalName(), TokenTypeEnum.CODE, code.getToken(), null);
        sessions.setBrandingAndRedirectUri(sessionId,
                session.getBranding(), request.getRedirectUri(), app.getId());
        // Keep the browser session marker: removing it before transaction commit makes retry impossible.
        return sessionId;
    }

    private void persistToken(Application app, UUID sessionId, String subject, TokenTypeEnum type,
            OAuth2Token token, SecretKey key) throws Exception {
        CreateTokenDto dto = new CreateTokenDto();
        dto.setApplicationId(app.getId());
        dto.setSessionId(sessionId);
        dto.setSubjectId(subject);
        dto.setTokenType(type);
        dto.setOpaque(type == TokenTypeEnum.CODE);
        dto.setData(token.getTokenValue());
        dto.setSigningKey(key);
        tokens.create(dto, token.getIssuedAt(), token.getExpiresAt(),
                token instanceof OAuth2AccessToken access ? access.getScopes() : null);
    }

    private static boolean changed(OAuth2Authorization.Token<?> next, OAuth2Authorization.Token<?> previous) {
        return next != null && (previous == null || !next.getToken().getTokenValue().equals(previous.getToken().getTokenValue()));
    }

    /** Persist only the authenticated identity and authority snapshot, never a password-bearing user object. */
    private static void normalizePrincipal(OAuth2Authorization authorization, OAuth2Authorization.Builder state) {
        Object principal = authorization.getAttribute(Principal.class.getName());
        if (principal instanceof Authentication authentication) {
            var authorities = authentication.getAuthorities().stream()
                    .map(a -> new SimpleGrantedAuthority(a.getAuthority())).toList();
            var snapshot = authentication.isAuthenticated()
                    ? UsernamePasswordAuthenticationToken.authenticated(authentication.getName(), null, authorities)
                    : UsernamePasswordAuthenticationToken.unauthenticated(authentication.getName(), null);
            state.attribute(Principal.class.getName(), snapshot);
        }
    }

    private UUID sessionId(OAuth2Authorization authorization) {
        String id = authorization.getAttribute(SESSION_ID);
        if (id == null) {
            throw new IllegalStateException("Authorization has no persisted session binding");
        }
        return UUID.fromString(id);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return active(authorizations.findById(id));
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        OAuth2Authorization authorization = active(authorizations.findByToken(token, tokenType));
        if (authorization != null && authorization.getRefreshToken() != null
                && authorization.getRefreshToken().getToken().getTokenValue().equals(token)) {
            return OAuth2Authorization.from(authorization).attribute(PRESENTED_REFRESH, token).build();
        }
        return authorization;
    }

    private OAuth2Authorization active(OAuth2Authorization authorization) {
        if (authorization == null) return null;
        return sessions.getBySessionId(sessionId(authorization))
                .filter(s -> s.getAuthSessionStatus() == AuthSessionStatusEnum.ACTIVE)
                .map(s -> authorization).orElse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(OAuth2Authorization authorization) {
        try {
            sessions.setSessionInactive(sessionId(authorization));
            authorizations.remove(authorization);
        } catch (Exception e) {
            throw new IllegalStateException("Could not revoke OAuth authorization", e);
        }
    }

    private OAuth2AuthenticationException invalidGrant() {
        return new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
    }
}
