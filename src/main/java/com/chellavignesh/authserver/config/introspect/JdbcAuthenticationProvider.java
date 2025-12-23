package com.chellavignesh.authserver.config.introspect;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.exception.AppNotFoundException;
import com.chellavignesh.authserver.config.JdbcOAuth2AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class JdbcAuthenticationProvider implements AuthenticationProvider {

    private final ApplicationService applicationService;
    private final JdbcOAuth2AuthorizationService authorizationService;

    @Autowired
    public JdbcAuthenticationProvider(ApplicationService applicationService, JdbcOAuth2AuthorizationService authorizationService) {

        this.applicationService = applicationService;
        this.authorizationService = authorizationService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        var auth = (OAuth2TokenIntrospectionAuthenticationToken) authentication;

        var principal = (BearerTokenAuthentication) authentication.getPrincipal();

        if (isTokenInvalid(principal)) {
            throw new AuthenticationServiceException("Provided bearer auth credentials are invalid.");
        }

        if (!Objects.equals(auth.getToken(), principal.getToken().getTokenValue()) && isTokenInvalid(auth)) {

            authentication.setAuthenticated(false);
            return authentication;
        }

        authentication.setAuthenticated(true);
        return authentication;
    }

    private boolean isTokenInvalid(Authentication authentication) {

        String token;

        if (authentication != null && authentication.getClass() == BearerTokenAuthentication.class) {

            var bearerTokenAuthentication = (BearerTokenAuthentication) authentication;

            token = bearerTokenAuthentication.getToken().getTokenValue();

        } else if (authentication != null && authentication.getClass() == OAuth2TokenIntrospectionAuthenticationToken.class) {

            token = ((OAuth2TokenIntrospectionAuthenticationToken) authentication).getToken();

        } else {
            throw new AuthenticationServiceException("");
        }

        OAuth2Authorization client = authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);

        if (client == null) {
            return true;
        }

        try {
            applicationService.getRegisteredClientByClientId(client.getRegisteredClientId());
        } catch (AppNotFoundException _) {
            return true;
        }

        return false;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BearerTokenAuthentication.class == authentication;
    }
}
