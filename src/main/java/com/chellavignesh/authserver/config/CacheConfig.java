package com.chellavignesh.authserver.config;

import com.chellavignesh.authserver.config.serialization.BinaryDataSerializers;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {

    @Value("${spring.cache.type:redis}")
    private String cacheType;

    @Value("${spring.cache.redis.key-prefix:agsup-auth:app:cache:}")
    private String cachePrefix;

    @Value("${cache.organization.ttl}")
    private long organizationTtl;

    @Value("${cache.application.ttl}")
    private long applicationTtl;

    @Value("${cache.token.ttl}")
    private long tokenTtl;

    @Value("${cache.session.ttl}")
    private long sessionTtl;

    @Value("${cache.registered-client.ttl}")
    private long registeredClientTtl;

    @Value("${cache.credential-secrets.ttl}")
    private long credentialSecretsTtl;

    @Value("${cache.token-by-value-hash.ttl}")
    private long tokenByValueHashTtl;

    @Value("${cache.external-source.ttl}")
    private long externalSourceTtl;

    /**
     * Redis distributed cache manager – PRIMARY for multi-pod deployments.
     * Shared cache across all pods eliminates redundant stored procedure calls.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    public CacheManager redisCacheManager(@Qualifier("cacheRedisTemplate") RedisTemplate<String, Object> cacheRedisTemplate) {

        log.info("🚀 Configuring Redis distributed cache manager for multi-pod deployment");

        Map<String, RedisCacheConfiguration> cacheConfigurations = getCacheSpecificConfigurations();

        RedisConnectionFactory connectionFactory = cacheRedisTemplate.getConnectionFactory();

        if (connectionFactory == null) {
            throw new IllegalStateException("Redis connection factory is not available");
        }

        RedisCacheManager cacheManager = RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(connectionFactory).cacheDefaults(getDefaultCacheConfiguration()).withInitialCacheConfigurations(cacheConfigurations).transactionAware().build();

        log.info("✅ Redis cache manager configured with {} cache definitions", cacheConfigurations.size());
        log.info("📦 Cache configurations: {}", cacheConfigurations.keySet());

        return cacheManager;
    }

    /**
     * Default Redis cache configuration.
     */
    private RedisCacheConfiguration getDefaultCacheConfiguration() {
        log.info("🔑 Using cache prefix from properties: {}", cachePrefix);

        return RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5)).serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())).serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer())).disableCachingNullValues().prefixCacheNameWith(cachePrefix);
    }

    /**
     * Cache-specific configurations optimized for production data patterns.
     */
    private Map<String, RedisCacheConfiguration> getCacheSpecificConfigurations() {

        Map<String, RedisCacheConfiguration> configurations = new HashMap<>();

        // Organization caches – ultra long TTL
        configurations.put("organization-get-by-id", createOptimizedCacheConfig(Duration.ofMillis(organizationTtl)));

        // Application caches
        configurations.put("application-get-by-id", createOptimizedCacheConfig(Duration.ofMillis(applicationTtl)));
        configurations.put("application-get-detail-by-id", createOptimizedCacheConfig(Duration.ofMillis(applicationTtl)));
        configurations.put("application-get-by-client-id", createOptimizedCacheConfig(Duration.ofMillis(applicationTtl)));
        configurations.put("application-get-settings-by-application-id", createOptimizedCacheConfig(Duration.ofMillis(applicationTtl)));
        configurations.put("application-get-redirect-uris", createOptimizedCacheConfig(Duration.ofMillis(applicationTtl)));
        configurations.put("application-get-logout-redirect-uris", createOptimizedCacheConfig(Duration.ofMillis(applicationTtl)));
        configurations.put("application-get-all-assigned-resources-by-client-id", createOptimizedCacheConfig(Duration.ofMillis(applicationTtl)));

        // Token settings caches
        configurations.put("token-settings-get-for-app", createOptimizedCacheConfig(Duration.ofMillis(applicationTtl)));
        configurations.put("token-settings-get-by-id", createOptimizedCacheConfig(Duration.ofMillis(applicationTtl)));

        // Session caches
        configurations.put("session-get-by-session-id", createOptimizedCacheConfig(Duration.ofMillis(sessionTtl)));

        // Credential secrets cache
        configurations.put("credential-secrets-by-app", createOptimizedCacheConfig(Duration.ofMillis(credentialSecretsTtl)));
        log.info("🔐 Configured credential-secrets cache: TTL={}ms", credentialSecretsTtl);

        // Token by value hash cache
        configurations.put("token-get-by-value-hash", createOptimizedCacheConfig(Duration.ofMillis(tokenByValueHashTtl)));
        log.info("⚡ Configured token-by-value-hash cache: TTL={}ms", tokenByValueHashTtl);

        // Registered client cache
        configurations.put("registered-client-by-client-id", createRegisteredClientCacheConfig(Duration.ofMillis(registeredClientTtl)));
        log.info("📌 Configured RegisteredClient cache: TTL={}ms", registeredClientTtl);

        // External source (branding) cache
        configurations.put("external-source-by-code", createOptimizedCacheConfig(Duration.ofMillis(externalSourceTtl)));
        log.info("🎨 Configured ExternalSource cache: TTL={}ms", externalSourceTtl);

        return configurations;
    }

    /**
     * RegisteredClient cache configuration using custom serializer.
     */
    private RedisCacheConfiguration createRegisteredClientCacheConfig(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig().entryTtl(ttl).disableCachingNullValues().prefixCacheNameWith(cachePrefix).serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())).serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new RegisteredClientRedisSerializer())).computePrefixWith(cacheName -> cachePrefix + cacheName + ":");
    }

    /**
     * Optimized cache configuration with efficient serialization.
     */
    private RedisCacheConfiguration createOptimizedCacheConfig(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig().entryTtl(ttl).disableCachingNullValues().prefixCacheNameWith(cachePrefix).serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())).serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(createOptimizedJsonSerializer())).computePrefixWith(cacheName -> cachePrefix + cacheName + ":");
    }

    /**
     * Binary-optimized JSON serializer.
     */
    private GenericJackson2JsonRedisSerializer createOptimizedJsonSerializer() {

        ObjectMapper mapper = new ObjectMapper();

        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        SimpleModule binaryModule = new SimpleModule("BinaryDataModule");
        binaryModule.addSerializer(byte[].class, new BinaryDataSerializers.ByteArrayBase64Serializer());
        binaryModule.addDeserializer(byte[].class, new BinaryDataSerializers.ByteArrayBase64Deserializer());
        mapper.registerModule(binaryModule);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);

        mapper.findAndRegisterModules();

        log.info("🔧 Binary-optimized Redis JSON serializer configured");

        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    /**
     * Fallback local cache manager for development/test environments.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "local")
    public CacheManager localCacheManager() {

        log.info("⚠️ Configuring fallback local cache manager (non-distributed)");

        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("application-get-by-id", "application-get-detail-by-id", "application-get-by-client-id", "application-get-settings-by-application-id", "application-get-redirect-uris", "application-get-logout-redirect-uris", "application-get-all-assigned-resources-by-client-id", "token-settings-get-for-app", "token-settings-get-by-id", "organization-get-by-id", "session-get-by-session-id", "credential-secrets-by-app", "registered-client-by-client-id", "external-source-by-code", "token-get-by-value-hash");

        cacheManager.setAllowNullValues(false);

        log.warn("🚨 Local cache manager configured – NOT suitable for multi-pod deployments");
        return cacheManager;
    }
}

