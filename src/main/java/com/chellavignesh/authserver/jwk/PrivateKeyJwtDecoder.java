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

            return Jwt.withTokenValue(token)
                    .headers(h -> h.putAll(headers))
                    .claims(c -> c.putAll(MappedJwtClaimSetConverter.withDefaults(Collections.emptyMap()).convert(claimsSet.getClaims())))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
