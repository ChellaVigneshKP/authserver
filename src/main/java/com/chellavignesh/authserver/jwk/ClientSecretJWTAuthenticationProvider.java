package com.chellavignesh.authserver.jwk;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.exception.AppNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class ClientSecretJWTAuthenticationProvider implements AuthenticationProvider {
    private static final String ERROR_URI = "https://chellavignesh.com/auth-server-error";
    private static final ClientAuthenticationMethod JWT_CLIENT_ASSERTION_AUTHENTICATION_METHOD = new ClientAuthenticationMethod("urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
    private JwtDecoderFactory<RegisteredClient> jwtDecoderFactory;

    private final Logger logger = LoggerFactory.getLogger(ClientSecretJWTAuthenticationProvider.class);
    private final ApplicationService applicationService;

    @Value("${client-secret-jwt.max-active}")
    private String maxActiveSecrets;

    @Autowired
    public ClientSecretJWTAuthenticationProvider(OAuth2AuthorizationService oAuth2AuthorizationService, ApplicationService applicationService) {
        this.jwtDecoderFactory = new ClientSecretJwtDecoderFactory();
        this.applicationService = applicationService;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2ClientAuthenticationToken clientAuthentication = (OAuth2ClientAuthenticationToken) authentication;
        if (!JWT_CLIENT_ASSERTION_AUTHENTICATION_METHOD.equals(clientAuthentication.getClientAuthenticationMethod())) {
            return null;
        }
        String clientId = clientAuthentication.getPrincipal().toString();
        RegisteredClient registeredClient = null;
        try {
            registeredClient = applicationService.getRegisteredClientByClientId(clientId, 1);
        } catch (AppNotFoundException _) {
            /*
            This is expected if there are no secrets for the client.
             */

        }
        if (registeredClient == null) {
            throwInvalidClient(OAuth2ParameterNames.CLIENT_ID);
        }
        if (this.logger.isTraceEnabled()) {
            this.logger.trace("Authenticated client: {}", registeredClient);
        }
        if (!registeredClient.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.CLIENT_SECRET_JWT)) {
            throwInvalidClient("authentication_method");
        }

        if (clientAuthentication.getCredentials() == null) {
            throwInvalidClient("credentials");
        }
        int i = 1;
        Jwt jwtAssertion = null;
        do {
            JwtDecoder jwtDecoder = this.jwtDecoderFactory.createDecoder(registeredClient);
            try {
                jwtAssertion = jwtDecoder.decode(clientAuthentication.getCredentials().toString());
                break;
            } catch (JwtException ex) {
                if (i < Integer.parseInt(maxActiveSecrets)) {
                    try {
                        registeredClient = applicationService.getRegisteredClientByClientId(clientId, i + 1);
                    } catch (AppNotFoundException _) {
                        /*
                        This is expected if there are no more secrets for the client.
                         */
                    }
                } else {
                    logger.warn("No active secrets found for client ID: {}", clientId);
                    throwInvalidClient(OAuth2ParameterNames.CLIENT_ASSERTION, ex);
                }
            }
            i++;
        } while (i <= Integer.parseInt(maxActiveSecrets));
        if (logger.isTraceEnabled()) {
            logger.trace("Successfully validated JWT assertion for client ID: {}", clientId);
        }
        return new OAuth2ClientAuthenticationToken(registeredClient, ClientAuthenticationMethod.CLIENT_SECRET_JWT, jwtAssertion);
    }

    public void setJwtDecoderFactory(JwtDecoderFactory<RegisteredClient> jwtDecoderFactory) {
        Assert.notNull(jwtDecoderFactory, "jwtDecoderFactory cannot be null");
        this.jwtDecoderFactory = jwtDecoderFactory;
    }

    private static void throwInvalidClient(String parameterName) {
        throwInvalidClient(parameterName, null);
    }

    private static void throwInvalidClient(String parameterName, Throwable cause) {
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_CLIENT,
                "Invalid client: " + parameterName + " is missing",
                ERROR_URI
        );
        throw new OAuth2AuthenticationException(error, error.toString(), cause);
    }
}
