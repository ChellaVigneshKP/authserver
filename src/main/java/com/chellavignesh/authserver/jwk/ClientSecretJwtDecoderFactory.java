package com.chellavignesh.authserver.jwk;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class ClientSecretJwtDecoderFactory implements JwtDecoderFactory<RegisteredClient> {
    public static final Function<RegisteredClient, OAuth2TokenValidator<Jwt>> DEFAULT_JWT_VALIDATOR_FACTORY = defaultJwtValidatorFactory();

    private static final String JWT_CLIENT_AUTHENTICATION_ERROR_URI = "https://chellavignesh.com/auth-server-error";
    private static final Map<JwsAlgorithm, String> JCA_ALGORITHM_MAPPINGS;

    static {
        Map<JwsAlgorithm, String> mappings = new HashMap<>();
        mappings.put(MacAlgorithm.HS256, "HmacSHA256");
        mappings.put(MacAlgorithm.HS384, "HmacSHA384");
        mappings.put(MacAlgorithm.HS512, "HmacSHA512");
        JCA_ALGORITHM_MAPPINGS = Collections.unmodifiableMap(mappings);
    }

    private static final RestTemplate restTemplate = createCongfiguredRestTemplate();


    private static RestTemplate createCongfiguredRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(15_000);
        requestFactory.setReadTimeout(15_000);
        if (System.getProperty("http.maxConnections") == null) {
            System.setProperty("http.maxConnections", "50");
        }
        return new RestTemplate(requestFactory);
    }

    private Function<RegisteredClient, OAuth2TokenValidator<Jwt>> jwtValidatorFactory = DEFAULT_JWT_VALIDATOR_FACTORY;

    @Override
    public JwtDecoder createDecoder(RegisteredClient registeredClient) {
        Assert.notNull(registeredClient, "registeredClient cannot be null");
        NimbusJwtDecoder jwtDecoder = buildDecoder(registeredClient);
        jwtDecoder.setJwtValidator(this.jwtValidatorFactory.apply(registeredClient));
        return jwtDecoder;
    }

    public void setJwtValidatorFactory(Function<RegisteredClient, OAuth2TokenValidator<Jwt>> jwtValidatorFactory) {
        Assert.notNull(jwtValidatorFactory, "jwtValidatorFactory cannot be null");
        this.jwtValidatorFactory = jwtValidatorFactory;
    }

    private static NimbusJwtDecoder buildDecoder(RegisteredClient registeredClient) {
        JwsAlgorithm jwsAlgorithm = registeredClient.getClientSettings().getTokenEndpointAuthenticationSigningAlgorithm();
        if (jwsAlgorithm instanceof SignatureAlgorithm) {
            String jwkSetUrl = registeredClient.getClientSettings().getJwkSetUrl();
            if (!StringUtils.hasText(jwkSetUrl)) {
                OAuth2Error oAuth2Error = new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT, "Failed to find a Signature verifier for the client: " + registeredClient.getClientId(), JWT_CLIENT_AUTHENTICATION_ERROR_URI);
                throw new OAuth2AuthenticationException(oAuth2Error);
            }
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUrl).jwsAlgorithm((SignatureAlgorithm) jwsAlgorithm).restOperations(restTemplate).build();
        }
        if (jwsAlgorithm instanceof MacAlgorithm) {
            String clientSecret = registeredClient.getClientSecret();
            if (!StringUtils.hasText(clientSecret)) {
                OAuth2Error oAuth2Error = new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT, "Failed to find a MAC verifier for the client: " + registeredClient.getClientId(), JWT_CLIENT_AUTHENTICATION_ERROR_URI);
                throw new OAuth2AuthenticationException(oAuth2Error);
            }
            SecretKeySpec secretKeySpec = new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), JCA_ALGORITHM_MAPPINGS.get(jwsAlgorithm));
            return NimbusJwtDecoder.withSecretKey(secretKeySpec).macAlgorithm((MacAlgorithm) jwsAlgorithm).build();
        }
        OAuth2Error oAuth2Error = new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT, "Unsupported JWS Algorithm: " + jwsAlgorithm, JWT_CLIENT_AUTHENTICATION_ERROR_URI);
        throw new OAuth2AuthenticationException(oAuth2Error);
    }

    private static Function<RegisteredClient, OAuth2TokenValidator<Jwt>> defaultJwtValidatorFactory() {
        return (registeredClient) -> {
            String clientId = registeredClient.getClientId();
            return new DelegatingOAuth2TokenValidator<>(
                    new JwtClaimValidator<>(JwtClaimNames.ISS, clientId::equals),
                    new JwtClaimValidator<>(JwtClaimNames.SUB, clientId::equals),
                    new JwtClaimValidator<>(JwtClaimNames.AUD, containsAudience()),
                    new JwtClaimValidator<>(JwtClaimNames.EXP, Objects::nonNull),
                    new JwtTimestampValidator()
            );
        };
    }

    private static Predicate<List<String>> containsAudience() {
        return (audienceClaim) -> {
            if (CollectionUtils.isEmpty(audienceClaim)) {
                return false;
            }
            List<String> audienceList = getAudience();
            for (String audience : audienceList) {
                if (audienceClaim.contains(audience)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static List<String> getAudience() {
        AuthorizationServerContext authorizationServerContext = AuthorizationServerContextHolder.getContext();
        if (!StringUtils.hasText(authorizationServerContext.getIssuer())) {
            return Collections.emptyList();
        }
        AuthorizationServerSettings authorizationServerSettings = authorizationServerContext.getAuthorizationServerSettings();
        List<String> audience = new ArrayList<>();
        audience.add(authorizationServerContext.getIssuer());
        audience.add(asUrl(authorizationServerContext.getIssuer(), authorizationServerSettings.getTokenEndpoint()));
        audience.add(asUrl(authorizationServerContext.getIssuer(), authorizationServerSettings.getTokenIntrospectionEndpoint()));
        audience.add(asUrl(authorizationServerContext.getIssuer(), authorizationServerSettings.getTokenRevocationEndpoint()));
        return audience;
    }

    private static String asUrl(String issuer, String endpoint) {
        return UriComponentsBuilder.fromUriString(issuer).path(endpoint).build().toUriString();
    }
}
