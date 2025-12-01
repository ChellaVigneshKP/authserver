package com.chellavignesh.authserver.jwk;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyConverter;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;

import java.security.Key;
import java.util.List;

public class PrivateKeySelector implements JWSKeySelector<SecurityContext> {
    private final String clientId;
    private final JWKService jwkService;

    public PrivateKeySelector(String clientId, JWKService jwkService) {
        this.clientId = clientId;
        this.jwkService = jwkService;
    }

    @Override
    public List<? extends Key> selectJWSKeys(JWSHeader jwsHeader, SecurityContext context) throws KeySourceException {
        List<JWK> jwkList = jwkService.get(clientId);
        return KeyConverter.toJavaKeys(jwkList);
    }
}
