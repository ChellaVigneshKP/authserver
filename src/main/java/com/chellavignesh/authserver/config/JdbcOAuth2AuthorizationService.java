package com.chellavignesh.authserver.config;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.TokenSettingsService;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.application.entity.TokenSettings;
import com.chellavignesh.authserver.authcode.AuthCodeService;
import com.chellavignesh.authserver.authcode.dto.CreateAuthCodeDto;
import com.chellavignesh.authserver.authcode.exception.AuthCodeCreationFailedException;
import com.chellavignesh.authserver.cms.BrandUrlMappingService;
import com.chellavignesh.authserver.enums.entity.AuthSessionStatusEnum;
import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import com.chellavignesh.authserver.keystore.exception.FailedToGenerateKeyException;
import com.chellavignesh.authserver.pkce.PkceService;
import com.chellavignesh.authserver.pkce.dto.CreatePkceDto;
import com.chellavignesh.authserver.pkce.entity.Pkce;
import com.chellavignesh.authserver.pkce.exception.PkceCreationFailedException;
import com.chellavignesh.authserver.session.AuthSessionService;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.session.exception.FailedToUpdateSessionException;
import com.chellavignesh.authserver.token.SigningKeyGenerator;
import com.chellavignesh.authserver.token.TokenService;
import com.chellavignesh.authserver.token.dto.CreateTokenDto;
import com.chellavignesh.authserver.token.entity.Token;
import com.chellavignesh.authserver.token.exception.TokenCreationFailedException;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Principal;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class JdbcOAuth2AuthorizationService implements OAuth2AuthorizationService {
    @Value("${server.base-path}")
    private String serverBasePath;
    private final TokenService tokenService;
    private final ApplicationService applicationService;
    private final AuthSessionService authSessionService;
    private final AuthCodeService authCodeService;
    private final PkceService pkceService;
    private final TokenSettingsService tokenSettingsService;
    private final SigningKeyGenerator signingKeyGenerator;
    private final BrandUrlMappingService brandUrlMappingService;

    @Autowired
    private HttpSession httpSession;

    @Autowired
    public JdbcOAuth2AuthorizationService(TokenService tokenService, ApplicationService applicationService, AuthSessionService authSessionService, AuthCodeService authCodeService, PkceService pkceService, TokenSettingsService tokenSettingsService, SigningKeyGenerator signingKeyGenerator, BrandUrlMappingService brandUrlMappingService) {

        this.tokenService = tokenService;
        this.applicationService = applicationService;
        this.authSessionService = authSessionService;
        this.authCodeService = authCodeService;
        this.pkceService = pkceService;
        this.tokenSettingsService = tokenSettingsService;
        this.signingKeyGenerator = signingKeyGenerator;
        this.brandUrlMappingService = brandUrlMappingService;
    }

    @Override
    public void save(OAuth2Authorization authorization) {

        try {
            Optional<Application> application = applicationService.getByClientId(authorization.getRegisteredClientId());

            if (application.isEmpty()) {
                log.error("Failed to locate registered application for client ID: {}", authorization.getRegisteredClientId());
                return;
            }

            Integer appId = application.get().getId();
            SecretKey secretKey;
            try {
                secretKey = signingKeyGenerator.generateKey();
            } catch (FailedToGenerateKeyException e) {
                log.error("Failed to generate signing key for app ID: {}", appId, e);
                return;
            }
            Integer orgId = application.get().getOrgId();

            Optional<TokenSettings> settings = tokenSettingsService.getForApp(orgId, appId);

            if (settings.isEmpty()) {
                log.warn("Failed to locate token settings for app ID: {}", appId);
            }
            Integer authCodeTtl = settings.map(TokenSettings::getAuthCodeTimeToLive).orElse(null);
            Integer accessTokenTtl = settings.map(TokenSettings::getAccessTokenTimeToLive).orElse(null);
            Integer refreshTokenTtl = settings.map(TokenSettings::getRefreshTokenTimeToLive).orElse(null);

            if (authorization.getAuthorizationGrantType() == AuthorizationGrantType.AUTHORIZATION_CODE) {
                Map<String, Object> attributes = authorization.getAttributes();
                OAuth2AuthorizationRequest request = (OAuth2AuthorizationRequest) attributes.get(OAuth2AuthorizationRequest.class.getCanonicalName());
                if (authorization.getAccessToken() == null) {
                    UUID sessionId = UUID.fromString(httpSession.getAttribute(ApplicationConstants.AUTH_SESSION_ID).toString());
                    Optional<AuthSession> authSession = authSessionService.getBySessionId(sessionId);
                    if (authSession.isEmpty()) {
                        throw new RuntimeException("Failed to locate auth session for session ID: " + sessionId);
                    }
                    String authCode = authorization.getToken(OAuth2AuthorizationCode.class).getToken().getTokenValue();
                    try {
                        createAuthCode(appId, sessionId, authCode);
                        createPkceRecord(appId, sessionId, request.getAdditionalParameters().get("code_challenge").toString(), request.getAdditionalParameters().get("code_challenge_method").toString(), request.getRedirectUri());
                        createToken(appId, sessionId, authorization.getPrincipalName(), TokenTypeEnum.CODE, true, authCode, null, authCodeTtl);
                        String branding = getBrandFromSession(httpSession);
                        String redirectUri = request.getRedirectUri();
                        log.debug("Updating Auth Session with branding and redirect URI. Branding: {}, Redirect URI: {} for sessionId: {}", branding, redirectUri, sessionId);
                        authSessionService.setBrandingAndRedirectUri(sessionId, branding, redirectUri, appId);
                        log.debug("Successfully updated Auth Session with branding and redirect URI for sessionId: {}", sessionId);
                        httpSession.removeAttribute(ApplicationConstants.AUTH_SESSION_ID);
                        log.debug("Removed AUTH_SESSION_ID from HttpSession for sessionId: {}", sessionId);
                    } catch (FailedToUpdateSessionException e) {
                        log.error("Failed to create auth code or PKCE record for session ID: {}", sessionId, e);
                        throw new RuntimeException(e);
                    } catch (Exception e) {
                        log.error("Unexpected error while creating auth code or PKCE record for session ID: {}", sessionId, e);
                        throw new RuntimeException(e);
                    }
                } else {
                    Optional<AuthSession> authSession = authSessionService.findSessionByAuthorization(authorization);
                    if (authSession.isEmpty()) {
                        throw new OAuth2AuthenticationException("Session not found by auth code. Auth code may be expired or may have already been used.");
                    }
                    authCodeService.setConsumedOn(authorization.getToken(OAuth2AuthorizationCode.class).getToken().getTokenValue());
                    UUID sessionId = authSession.get().getSessionId();
                    createToken(appId, sessionId, authorization.getPrincipalName(), TokenTypeEnum.ACCESS_TOKEN, false, authorization.getAccessToken().getToken().getTokenValue(), secretKey, accessTokenTtl);
                }
            } else {
                AuthSession authSession = authSessionService.createSession(application, authorization);
                if (authSession == null) {
                    throw new RuntimeException("Failed to create auth session for app ID: " + appId);
                }


                createToken(appId, authSession.getSessionId(), authorization.getPrincipalName(), TokenTypeEnum.ACCESS_TOKEN, false, authorization.getAccessToken().getToken().getTokenValue(), secretKey, accessTokenTtl);

                if (authorization.getRefreshToken() != null) {
                    createToken(appId, authSession.getSessionId(), authorization.getPrincipalName(), TokenTypeEnum.REFRESH_TOKEN, false, authorization.getRefreshToken().getToken().getTokenValue(), secretKey, refreshTokenTtl);
                }
            }

        } catch (Exception e) {
            log.error("Failed to save OAuth2Authorization", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return findBySessionId(UUID.fromString(id));
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return tokenService.getByValue(token, TokenTypeEnum.fromOAuth2TokenType(tokenType)).map(t -> findBySessionId(t.getSessionId())).orElse(null);
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        try {
            authSessionService.setSessionInactive(UUID.fromString(authorization.getId()));
        } catch (FailedToUpdateSessionException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private OAuth2Authorization findBySessionId(UUID sessionId) {

        Optional<AuthSession> authSession = authSessionService.getBySessionId(sessionId);
        if (authSession.isEmpty() || authSession.get().getAuthSessionStatus() == AuthSessionStatusEnum.INACTIVE) {
            return null;
        }

        Optional<Application> application = applicationService.getById(authSession.get().getApplicationId());
        if (application.isEmpty()) {
            return null;
        }

        List<Token> tokens = tokenService.getAllActiveBySessionId(sessionId);

        Token accessToken = null;
        Token refreshToken = null;
        Token authCodeToken = null;

        for (Token token : tokens) {
            switch (token.getTokenType()) {
                case ACCESS_TOKEN:
                    accessToken = token;
                    break;
                case REFRESH_TOKEN:
                    refreshToken = token;
                    break;
                case ID_TOKEN:
                    break;
                case CODE:
                    authCodeToken = token;
                    break;
            }
        }

        if (accessToken == null && refreshToken == null && authCodeToken == null) {
            return null; // TODO: clarify session management expectations
        }

        RegisteredClient registeredClient;
        try {
            registeredClient = applicationService.getRegisteredClientById(authSession.get().getApplicationId());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        OAuth2Authorization.Builder authBuilder = OAuth2Authorization.withRegisteredClient(registeredClient).id(sessionId.toString());

        if (accessToken != null) {
            Instant createdOn = accessToken.getCreatedOn() != null ? Instant.ofEpochMilli(accessToken.getCreatedOn().getTime()) : null;

            Instant expiration = accessToken.getExpiration() != null ? Instant.ofEpochMilli(accessToken.getExpiration().getTime()) : null;

            Set<String> scopes = authSession.get().getScopes();

            authBuilder.accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, accessToken.getData(), createdOn, expiration, scopes)).authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS);

            Map<String, Object> claims = new HashMap<>();
            for (String scope : scopes) {
                claims.put(scope, scope);
            }

            authBuilder.token(new OidcIdToken(UUID.randomUUID().toString(), createdOn, expiration, claims));
        }

        if (refreshToken != null) {
            Instant createdOn = refreshToken.getCreatedOn() != null ? Instant.ofEpochMilli(refreshToken.getCreatedOn().getTime()) : null;

            Instant expiration = refreshToken.getExpiration() != null ? Instant.ofEpochMilli(refreshToken.getExpiration().getTime()) : null;

            authBuilder.refreshToken(new OAuth2RefreshToken(refreshToken.getData(), createdOn, expiration)).authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
        }

        if (authCodeToken != null) {
            Optional<Pkce> pkceRecord = pkceService.getBySessionId(authSession.get().getSessionId());
            if (pkceRecord.isEmpty()) {
                return null;
            }

            String codeChallenge = pkceRecord.get().getData();
            String codeChallengeMethod = pkceRecord.get().getAlgorithm();
            String redirectUri = getRedirectUri(pkceRecord.get(), registeredClient);

            OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode().clientId(registeredClient.getClientId()).authorizationUri(brandUrlMappingService.getUrlByBrand(getBrandFromSession(httpSession)) + "/oauth2/authorize").redirectUri(redirectUri).additionalParameters(params -> params.put("code_challenge", codeChallenge)).additionalParameters(params -> params.put("code_challenge_method", codeChallengeMethod)).build();

            Instant createdOn = authCodeToken.getCreatedOn() != null ? Instant.ofEpochMilli(authCodeToken.getCreatedOn().getTime()) : null;

            Instant expiration = authCodeToken.getExpiration() != null ? Instant.ofEpochMilli(authCodeToken.getExpiration().getTime()) : null;

            authBuilder.token(new OAuth2AuthorizationCode(authCodeToken.getData(), createdOn, expiration)).attribute(request.getClass().getName(), request).attribute(Principal.class.getName(), new UsernamePasswordAuthenticationToken(registeredClient.getClientId(), null)).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
        }

        return authBuilder.id(authSession.get().getSessionId().toString()).authorizedScopes(authSession.get().getScopes()).principalName(authSession.get().getSubjectId()).build();
    }

    private String getRedirectUri(Pkce pkceRecord, RegisteredClient registeredClient) {
        String redirectUri = "";
        String pkceRedirectUri = pkceRecord.getRedirectUri();

        Set<String> registeredClientRedirectUris = registeredClient.getRedirectUris();

        for (String uri : registeredClientRedirectUris) {
            if (uri.contains(pkceRedirectUri)) {
                redirectUri = uri;
                break;
            }
        }

        return redirectUri;
    }

    private void createToken(Integer applicationId, UUID sessionId, String subjectId, TokenTypeEnum tokenType, Boolean isOpaque, String value, SecretKey signingKey, Integer timeToLive) {

        CreateTokenDto tokenDto = new CreateTokenDto();
        tokenDto.setApplicationId(applicationId);
        tokenDto.setTokenType(tokenType);
        tokenDto.setSessionId(sessionId);
        tokenDto.setOpaque(isOpaque);
        tokenDto.setSubjectId(subjectId);
        tokenDto.setData(value);
        tokenDto.setSigningKey(signingKey);
        tokenDto.setTimeToLive(timeToLive);

        try {
            tokenService.create(tokenDto);
        } catch (TokenCreationFailedException e) {
            log.error(e.getMessage());
        }
    }

    private void createAuthCode(Integer applicationId, UUID sessionId, String value) {
        CreateAuthCodeDto authCodeDto = new CreateAuthCodeDto();
        authCodeDto.setApplicationId(applicationId);
        authCodeDto.setSessionId(sessionId);
        authCodeDto.setData(value);

        try {
            authCodeService.create(authCodeDto);
        } catch (AuthCodeCreationFailedException e) {
            log.error(e.getMessage());
        }
    }

    private void createPkceRecord(Integer applicationId, UUID sessionId, String challenge, String algorithm, String redirectUri) {
        CreatePkceDto pkceDto = new CreatePkceDto();
        pkceDto.setApplicationId(applicationId);
        pkceDto.setSessionId(sessionId);
        pkceDto.setData(challenge);
        pkceDto.setAlgorithm(algorithm);
        pkceDto.setRedirectUri(redirectUri);

        try {
            pkceService.create(pkceDto);
        } catch (PkceCreationFailedException e) {
            log.error(e.getMessage());
        }
    }

    private String getBrandFromSession(HttpSession session) {
        try {
            return (String) session.getAttribute(ApplicationConstants.BRANDING_INFO);
        } catch (Exception e) {
            log.error("Failed to get the brand from the HttpSession", e);
            return null;
        }
    }

}
