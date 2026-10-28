package com.chellavignesh.authserver.jwk;


import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class PrivateKeyJwtDecoder {
    private final JWKService jwkService;

    public PrivateKeyJwtDecoder(JWKService jwkService) {
        this.jwkService = jwkService;
    }

    public Jwt decode(String token, String clientId) {
        try {
            ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
            jwtProcessor.setJWSKeySelector(new PrivateKeySelector(clientId, jwkService));
            JWT parsedJWT = JWTParser.parse(token);
            JWTClaimsSet claimsSet = jwtProcessor.process(parsedJWT, null);
            Map<String, Object> headers = new LinkedHashMap<>(parsedJWT.getHeader().toJSONObject());

            Jwt jwt = Jwt.withTokenValue(token)
                    .headers(h -> h.putAll(headers))
                    .claims(c -> c.putAll(MappedJwtClaimSetConverter.withDefaults(Collections.emptyMap()).convert(claimsSet.getClaims())))
                    .build();
            var validation = ClientSecretJwtDecoderFactory.DEFAULT_JWT_VALIDATOR_FACTORY
                    .apply(org.springframework.security.oauth2.server.authorization.client.RegisteredClient.withId(clientId)
                            .clientId(clientId).authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS)
                            .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.PRIVATE_KEY_JWT).build())
                    .validate(jwt);
            if (validation.hasErrors()) {
                throw new org.springframework.security.oauth2.jwt.JwtValidationException("Invalid client assertion claims", validation.getErrors());
            }
            return jwt;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
