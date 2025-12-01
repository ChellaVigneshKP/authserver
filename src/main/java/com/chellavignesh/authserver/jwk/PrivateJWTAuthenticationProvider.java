package com.chellavignesh.authserver.jwk;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Service;

@Service
public class PrivateJWTAuthenticationProvider implements AuthenticationProvider {
    private static final Logger logger = LoggerFactory.getLogger(PrivateJWTAuthenticationProvider.class);

    private static final ClientAuthenticationMethod EXPECTED_CLIENT_AUTHENTICATION_METHOD = new ClientAuthenticationMethod("urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
    private final ApplicationService applicationService;
    private final JWKService jwkService;

    @Autowired
    public PrivateJWTAuthenticationProvider(ApplicationService applicationService, JWKService jwkService) {
        this.applicationService = applicationService;
        this.jwkService = jwkService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2ClientAuthenticationToken clientAuthenticationToken = (OAuth2ClientAuthenticationToken) authentication;
        if (!EXPECTED_CLIENT_AUTHENTICATION_METHOD.equals(clientAuthenticationToken.getClientAuthenticationMethod()))
            return null;
        String clientId = clientAuthenticationToken.getPrincipal().toString();
        RegisteredClient registeredClient;
        try {
            registeredClient = applicationService.getRegisteredClientByClientId(clientId);
            if (registeredClient == null) {
                logger.warn("No registered client found for client ID: {}", clientId);
                return null;
            }
        } catch (Exception e) {
            logger.warn("Error while fetching registered client for client ID: {}", clientId, e);
            return null;
        }
        if (!registeredClient.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.PRIVATE_KEY_JWT)) {
            logger.trace("Client authentication method is not supported for client ID: {}", clientId);
            return null;
        }
        Jwt jwt;
        try {
            PrivateKeyJwtDecoder decoder = new PrivateKeyJwtDecoder(jwkService);
            jwt = decoder.decode(clientAuthenticationToken.getCredentials().toString(), clientId);
        } catch (Exception e) {
            logger.warn("Error while decoding JWT for client ID: {}", clientId, e);
            return null;
        }
        return new OAuth2ClientAuthenticationToken(registeredClient, ClientAuthenticationMethod.PRIVATE_KEY_JWT, jwt);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
