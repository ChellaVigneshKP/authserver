package com.chellavignesh.authserver.config.introspect;

import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import com.chellavignesh.authserver.token.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class TokenIntrospector implements OpaqueTokenIntrospector {

    private final TokenService tokenService;

    @Autowired
    public TokenIntrospector(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {

        try {
            var maybeToken = tokenService.getByValue(token, TokenTypeEnum.fromOAuth2TokenType(OAuth2TokenType.ACCESS_TOKEN));

            return new OAuth2IntrospectionAuthenticatedPrincipal(tokenService.getClaimsForToken(maybeToken.orElseThrow()), Collections.emptyList());

        } catch (Exception _) {
            throw new BadOpaqueTokenException("Token is not active or doesn't exist");
        }
    }
}
