package com.chellavignesh.authserver.config.introspect;

import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import com.chellavignesh.authserver.token.TokenService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.stereotype.Service;

@Service
public class IntrospectionAuthenticationProvider implements AuthenticationProvider {

    private final TokenService tokenService;

    public IntrospectionAuthenticationProvider(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        var auth = (OAuth2TokenIntrospectionAuthenticationToken) authentication;

        try {
            var principal = (BearerTokenAuthentication) authentication.getPrincipal();

            var token = tokenService.getByValue(auth.getToken(), TokenTypeEnum.fromOAuth2TokenType(OAuth2TokenType.ACCESS_TOKEN)).orElseThrow();

            var claims = tokenService.getClaimsForToken(token);

            var tokenAuth = new OAuth2TokenIntrospectionAuthenticationToken(auth.getToken(), principal,
                    // Throws exception if scope claims are not in a List (i.e. they're in a Set)
                    OAuth2TokenIntrospection.withClaims(claims).build());

            tokenAuth.setAuthenticated(isAuthenticated(tokenAuth.getTokenClaims().getClaimAsBoolean("active")));

            return tokenAuth;

        } catch (Exception _) {
            return auth;
        }
    }

    private boolean isAuthenticated(Boolean isActive) {
        return Boolean.TRUE.equals(isActive);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2TokenIntrospectionAuthenticationToken.class == authentication;
    }
}
