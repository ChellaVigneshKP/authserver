package com.chellavignesh.authserver.session;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.enums.entity.AuthSessionStatusEnum;
import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.session.exception.FailedToUpdateSessionException;
import com.chellavignesh.authserver.token.TokenService;
import com.chellavignesh.authserver.token.entity.Token;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class LogoutService {

    private final TokenService tokenService;
    private final AuthSessionService authSessionService;
    private final ApplicationService applicationService;
    private final org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService authorizations;

    public LogoutService(TokenService tokenService, AuthSessionService authSessionService, ApplicationService applicationService, org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService authorizations) {
        this.tokenService = tokenService;
        this.authSessionService = authSessionService;
        this.applicationService = applicationService;
        this.authorizations = authorizations;
    }

    public String logoutSession(String proofToken, String clientId, String redirectUrl) throws FailedToUpdateSessionException {

        var token = getToken(proofToken);
        var session = getSession(token.getSessionId());
        var application = getApplication(clientId);

        if (!Objects.equals(session.getApplicationId(), application.getId())) {
            throw new IllegalArgumentException("Client ID does not match token session");
        }

        var redirectUrls = applicationService.getApplicationLogoutRedirectUris(application.getId());

        if (!redirectUrls.contains(redirectUrl)) {
            throw new IllegalArgumentException("Redirect URI not included for client");
        }

        authSessionService.setSessionInactive(session.getSessionId());
        return redirectUrl;
    }

    private Token getToken(String token) {
        var authorization = authorizations.findByToken(token,
                new org.springframework.security.oauth2.server.authorization.OAuth2TokenType("id_token"));
        if (authorization != null && authorization.getAccessToken() != null) {
            return tokenService.getByValue(authorization.getAccessToken().getToken().getTokenValue(),
                    TokenTypeEnum.ACCESS_TOKEN).orElseThrow(() -> new IllegalArgumentException("Token not found"));
        }
        // Preserve the existing opaque proof-token contract for older clients.
        return tokenService.getByValue(token, TokenTypeEnum.ACCESS_TOKEN)
                .orElseThrow(() -> new IllegalArgumentException("Token not found"));
    }

    private AuthSession getSession(UUID sessionId) {
        var session = authSessionService.getBySessionId(sessionId).orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (session.getAuthSessionStatus() != AuthSessionStatusEnum.ACTIVE) {
            throw new IllegalArgumentException("Session is not active");
        }

        return session;
    }

    private Application getApplication(String clientId) {
        return applicationService.getByClientId(clientId).orElseThrow(() -> new IllegalArgumentException("Client not found"));
    }
}

