package com.chellavignesh.authserver.jose;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/** Stable signing identity across process restarts. Missing keys fail startup explicitly. */
@Configuration
public class KeyGenerator {
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(ResourceLoader resources,
            @Value("${auth.signing.keystore}") String location,
            @Value("${key-store.password}") String password,
            @Value("${auth.signing.alias:authserver-signing}") String alias) throws Exception {
        var store=KeyStore.getInstance("PKCS12");
        try(var input=resources.getResource(location).getInputStream()) {
            store.load(input,password.toCharArray());
        }
        if (!(store.getKey(alias,password.toCharArray()) instanceof RSAPrivateKey privateKey)
                || store.getCertificate(alias)==null
                || !(store.getCertificate(alias).getPublicKey() instanceof RSAPublicKey publicKey)) {
            throw new IllegalStateException("Signing keystore must contain an RSA private key and certificate at "+alias);
        }
        var key=new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        key=new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
        var keys=new JWKSet(key);
        return (selector,context)->selector.select(keys);
    }
}
