package com.chellavignesh.authserver.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public class RegisteredClientRedisSerializer implements RedisSerializer<RegisteredClient> {

    private static final Logger log =
            LoggerFactory.getLogger(RegisteredClientRedisSerializer.class);

    private final ObjectMapper objectMapper;

    public RegisteredClientRedisSerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public byte[] serialize(@Nullable RegisteredClient registeredClient)
            throws SerializationException {

        if (registeredClient == null) {
            return new byte[0];
        }

        try {
            RegisteredClientDto dto = toDto(registeredClient);
            byte[] bytes = objectMapper.writeValueAsBytes(dto);

            log.debug("✅ Serialized RegisteredClient for clientId: {} (size: {} bytes)",
                    registeredClient.getClientId(), bytes.length);

            return bytes;
        } catch (JsonProcessingException e) {
            log.error("❌ Failed to serialize RegisteredClient: {}", e.getMessage(), e);
            throw new SerializationException("Failed to serialize RegisteredClient", e);
        }
    }

    @Override
    public RegisteredClient deserialize(@Nullable byte[] bytes)
            throws SerializationException {

        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try {
            RegisteredClientDto dto =
                    objectMapper.readValue(bytes, RegisteredClientDto.class);

            RegisteredClient registeredClient = fromDto(dto);

            log.debug("✅ Deserialized RegisteredClient for clientId: {}",
                    registeredClient.getClientId());

            return registeredClient;
        } catch (IOException e) {
            log.error("❌ Failed to deserialize RegisteredClient: {}", e.getMessage(), e);
            throw new SerializationException("Failed to deserialize RegisteredClient", e);
        }
    }

    /**
     * Convert RegisteredClient to DTO
     */
    private RegisteredClientDto toDto(RegisteredClient client) {

        RegisteredClientDto dto = new RegisteredClientDto();

        dto.setId(client.getId());
        dto.setClientId(client.getClientId());
        dto.setClientIdIssuedAt(client.getClientIdIssuedAt());
        dto.setClientSecret(client.getClientSecret());
        dto.setClientSecretExpiresAt(client.getClientSecretExpiresAt());
        dto.setClientName(client.getClientName());

        dto.setClientAuthenticationMethods(new HashSet<>());
        client.getClientAuthenticationMethods().forEach(
                m -> dto.getClientAuthenticationMethods().add(m.getValue())
        );

        dto.setAuthorizationGrantTypes(new HashSet<>());
        client.getAuthorizationGrantTypes().forEach(
                g -> dto.getAuthorizationGrantTypes().add(g.getValue())
        );

        dto.setRedirectUris(new HashSet<>(client.getRedirectUris()));
        dto.setPostLogoutRedirectUris(new HashSet<>(client.getPostLogoutRedirectUris()));
        dto.setScopes(new HashSet<>(client.getScopes()));

        ClientSettings cs = client.getClientSettings();
        dto.setRequireProofKey(cs.isRequireProofKey());
        dto.setRequireAuthorizationConsent(cs.isRequireAuthorizationConsent());
        dto.setJwkSetUrl(cs.getJwkSetUrl());

        if (cs.getTokenEndpointAuthenticationSigningAlgorithm() != null) {
            dto.setTokenEndpointAuthSigningAlgorithm(
                    cs.getTokenEndpointAuthenticationSigningAlgorithm().getName());
        }

        TokenSettings ts = client.getTokenSettings();
        dto.setAccessTokenTimeToLive(ts.getAccessTokenTimeToLive());
        dto.setRefreshTokenTimeToLive(ts.getRefreshTokenTimeToLive());
        dto.setAuthorizationCodeTimeToLive(ts.getAuthorizationCodeTimeToLive());
        dto.setDeviceCodeTimeToLive(ts.getDeviceCodeTimeToLive());
        dto.setReuseRefreshTokens(ts.isReuseRefreshTokens());

        if (ts.getAccessTokenFormat() != null) {
            dto.setAccessTokenFormat(ts.getAccessTokenFormat().getValue());
        }

        if (ts.getIdTokenSignatureAlgorithm() != null) {
            dto.setIdTokenSignatureAlgorithm(
                    ts.getIdTokenSignatureAlgorithm().getName());
        }

        return dto;
    }

    /**
     * Convert DTO back to RegisteredClient
     */
    private RegisteredClient fromDto(RegisteredClientDto dto) {

        RegisteredClient.Builder builder =
                RegisteredClient.withId(dto.getId())
                        .clientId(dto.getClientId())
                        .clientIdIssuedAt(dto.getClientIdIssuedAt())
                        .clientSecret(dto.getClientSecret())
                        .clientSecretExpiresAt(dto.getClientSecretExpiresAt())
                        .clientName(dto.getClientName());

        dto.getClientAuthenticationMethods()
                .forEach(m -> builder.clientAuthenticationMethod(
                        new ClientAuthenticationMethod(m)));

        dto.getAuthorizationGrantTypes()
                .forEach(g -> builder.authorizationGrantType(
                        new AuthorizationGrantType(g)));

        dto.getRedirectUris().forEach(builder::redirectUri);
        dto.getPostLogoutRedirectUris().forEach(builder::postLogoutRedirectUri);
        dto.getScopes().forEach(builder::scope);

        ClientSettings.Builder csb = ClientSettings.builder()
                .requireProofKey(dto.isRequireProofKey())
                .requireAuthorizationConsent(dto.isRequireAuthorizationConsent())
                .jwkSetUrl(dto.getJwkSetUrl());

        if (dto.getTokenEndpointAuthSigningAlgorithm() != null) {
            SignatureAlgorithm alg =
                    SignatureAlgorithm.from(dto.getTokenEndpointAuthSigningAlgorithm());
            if (alg != null) {
                csb.tokenEndpointAuthenticationSigningAlgorithm(alg);
            }
        }

        builder.clientSettings(csb.build());

        TokenSettings.Builder tsb = TokenSettings.builder()
                .accessTokenTimeToLive(dto.getAccessTokenTimeToLive())
                .refreshTokenTimeToLive(dto.getRefreshTokenTimeToLive())
                .authorizationCodeTimeToLive(dto.getAuthorizationCodeTimeToLive())
                .deviceCodeTimeToLive(dto.getDeviceCodeTimeToLive())
                .reuseRefreshTokens(dto.isReuseRefreshTokens());

        if (dto.getAccessTokenFormat() != null) {
            tsb.accessTokenFormat(new OAuth2TokenFormat(dto.getAccessTokenFormat()));
        }

        if (dto.getIdTokenSignatureAlgorithm() != null) {
            SignatureAlgorithm alg =
                    SignatureAlgorithm.from(dto.getIdTokenSignatureAlgorithm());
            if (alg != null) {
                tsb.idTokenSignatureAlgorithm(alg);
            }
        }

        builder.tokenSettings(tsb.build());

        return builder.build();
    }

    @Getter
    @Setter
    public static class RegisteredClientDto {
        private String id;
        private String clientId;
        private Instant clientIdIssuedAt;
        private String clientSecret;
        private Instant clientSecretExpiresAt;
        private String clientName;

        private Set<String> clientAuthenticationMethods;
        private Set<String> authorizationGrantTypes;
        private Set<String> redirectUris;
        private Set<String> postLogoutRedirectUris;
        private Set<String> scopes;

        private boolean requireProofKey;
        private boolean requireAuthorizationConsent;
        private String jwkSetUrl;
        private String tokenEndpointAuthSigningAlgorithm;

        private Duration accessTokenTimeToLive;
        private Duration refreshTokenTimeToLive;
        private Duration authorizationCodeTimeToLive;
        private Duration deviceCodeTimeToLive;
        private boolean reuseRefreshTokens;
        private String accessTokenFormat;
        private String idTokenSignatureAlgorithm;
    }
}

