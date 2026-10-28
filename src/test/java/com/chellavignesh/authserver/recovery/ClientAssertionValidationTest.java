package com.chellavignesh.authserver.recovery;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.jwk.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientAssertionValidationTest {
    final String secret="abcdefghijklmnopqrstuvwxyz1234567890ABCD";
    final String issuer="http://localhost:9080/services";
    RegisteredClient client() { return RegisteredClient.withId("client").clientId("client").clientSecret(secret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_JWT)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .clientSettings(ClientSettings.builder().tokenEndpointAuthenticationSigningAlgorithm(MacAlgorithm.HS256).build()).build(); }
    @BeforeEach void context() {
        var context=mock(AuthorizationServerContext.class);when(context.getIssuer()).thenReturn(issuer);
        when(context.getAuthorizationServerSettings()).thenReturn(AuthorizationServerSettings.builder().issuer(issuer).build());
        AuthorizationServerContextHolder.setContext(context);
    }
    @AfterEach void clear() {AuthorizationServerContextHolder.resetContext();}
    String assertion(String sub,String aud,Instant expiration) throws Exception {
        var jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),new JWTClaimsSet.Builder().issuer("client")
                .subject(sub).audience(aud).expirationTime(expiration==null?null:Date.from(expiration)).build());
        jwt.sign(new MACSigner(secret));return jwt.serialize();
    }
    @Test void acceptsAdvertisedTokenEndpointAndRejectsWrongIdentityAudienceAndExpiredAssertion() throws Exception {
        var decoder=new ClientSecretJwtDecoderFactory().createDecoder(client());
        assertThat(decoder.decode(assertion("client",issuer+"/oauth2/token",Instant.now().plusSeconds(60))).getSubject()).isEqualTo("client");
        assertThatThrownBy(()->decoder.decode(assertion("other",issuer+"/oauth2/token",Instant.now().plusSeconds(60)))).isInstanceOf(JwtException.class);
        assertThatThrownBy(()->decoder.decode(assertion("client","https://wrong.test/token",Instant.now().plusSeconds(60)))).isInstanceOf(JwtException.class);
        assertThatThrownBy(()->decoder.decode(assertion("client",issuer+"/oauth2/token",Instant.now().minusSeconds(120)))).isInstanceOf(JwtException.class);
        assertThatThrownBy(()->decoder.decode(assertion("client",issuer+"/oauth2/token",null))).isInstanceOf(JwtException.class);
    }
    @Test void invalidAssertionWithOneSecretFailsAsInvalidClientWhenRetrySlotIsAbsent() throws Exception {
        var applications=mock(ApplicationService.class);
        when(applications.getRegisteredClientByClientId("client",1)).thenReturn(client());
        when(applications.getRegisteredClientByClientId("client",2)).thenReturn(null);
        var provider=new ClientSecretJWTAuthenticationProvider(mock(OAuth2AuthorizationService.class),applications);
        ReflectionTestUtils.setField(provider,"maxActiveSecrets","2");
        var authentication=new OAuth2ClientAuthenticationToken("client",new ClientAuthenticationMethod("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"),"invalid",Map.of());
        assertThatThrownBy(()->provider.authenticate(authentication)).isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e->assertThat(((OAuth2AuthenticationException)e).getError().getErrorCode()).isEqualTo("invalid_client"));
    }
}
