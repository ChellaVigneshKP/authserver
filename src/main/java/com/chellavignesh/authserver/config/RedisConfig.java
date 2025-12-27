package com.chellavignesh.authserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.api.StatefulConnection;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;

@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.data.redis.timeout:2000}")
    private int redisTimeoutMs;

    @Value("${spring.data.redis.lettuce.pool.max-active:8}")
    private int maxActive;

    @Value("${spring.data.redis.lettuce.pool.max-idle:8}")
    private int maxIdle;

    @Value("${spring.data.redis.lettuce.pool.min-idle:0}")
    private int minIdle;

    @Value("${spring.data.redis.lettuce.pool.max-wait:-1}")
    private long maxWaitMs;

    @Value("${spring.data.redis.lettuce.shutdown-timeout:100}")
    private long shutdownTimeoutMs;

    @Value("${spring.data.redis.lettuce.pool.test-on-borrow:true}")
    private boolean testOnBorrow;

    @Value("${spring.data.redis.lettuce.pool.test-while-idle:true}")
    private boolean testWhileIdle;

    @Value("${spring.data.redis.lettuce.pool.time-between-eviction-runs:30000}")
    private long timeBetweenEvictionRuns;

    @Value("${spring.data.redis.sentinel.enabled:false}")
    private boolean sentinelEnabled;

    @Value("${spring.data.redis.sentinel.master:}")
    private String sentinelMaster;

    @Value("${spring.data.redis.sentinel.nodes:}")
    private String sentinelNodes;

    @Value("${spring.data.redis.sentinel.password:}")
    private String sentinelPassword;

    @Value("${spring.data.redis.cache.database:1}")
    private int cacheDatabase;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${spring.data.redis.health-check.fail-fast:true}")
    private boolean healthCheckFailFast;

    /**
     * Creates shared ClientResources for Lettuce connections.
     * This improves performance by reusing I/O and computation threads.
     */
    @Bean(destroyMethod = "shutdown")
    public ClientResources clientResources() {
        int ioThreads = Math.max(Runtime.getRuntime().availableProcessors(), 8);
        log.info("Creating Lettuce ClientResources with {} I/O threads and {} computation threads", ioThreads, ioThreads);

        return DefaultClientResources.builder()
                .ioThreadPoolSize(ioThreads)
                .computationThreadPoolSize(ioThreads)
                .build();
    }

    /**
     * Creates the connection pool configuration for Lettuce.
     * Fixed: Now returns the proper type for Lettuce pooling
     */
    @Bean
    public GenericObjectPoolConfig<StatefulConnection<?, ?>> redisPoolConfig() {
        log.info("Creating connection pool configuration");
        log.info(" - Max active connections: {}", maxActive);
        log.info(" - Max idle connections: {}", maxIdle);
        log.info(" - Min idle connections: {}", minIdle);
        log.info(" - Max wait time: {} ms", maxWaitMs);

        GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(maxActive);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setMaxWaitMillis(maxWaitMs);
        poolConfig.setTestOnBorrow(testOnBorrow);
        poolConfig.setTestWhileIdle(testWhileIdle);
        poolConfig.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRuns);

        return poolConfig;
    }

    /**
     * Configures the Redis connection factory using Lettuce driver.
     * Supports both standalone and sentinel modes.
     */
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory(
            ClientResources clientResources,
            GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig) {

        log.info("--------------------------------------------------");
        log.info("Starting Redis Connection Factory Configuration");
        log.info("Configuration mode: {}", sentinelEnabled ? "SENTINEL (High Availability)" : "STANDALONE");

        LettuceConnectionFactory factory;

        try {
            if (sentinelEnabled) {
                log.info("Creating Sentinel connection factory...");
                factory = createSentinelConnectionFactory(clientResources, poolConfig);
                log.info("Sentinel connection factory created successfully");
            } else {
                log.info("Creating standalone connection factory...");
                factory = createStandaloneConnectionFactory(clientResources, poolConfig);
                log.info("Standalone connection factory created successfully");
            }

            log.info("--------------------------------------------------");
            log.info("Redis connection factory configured successfully");
            log.info("--------------------------------------------------");

            return factory;

        } catch (Exception e) {
            log.error("--------------------------------------------------");
            log.error("FATAL: Failed to configure Redis connection factory");
            log.error("--------------------------------------------------");
            log.error("Error type: {}", e.getClass().getSimpleName());
            log.error("Error message: {}", e.getMessage());
            log.error("Stack trace:", e);
            throw new IllegalStateException("Failed to create Redis connection factory", e);
        }
    }

    private LettuceConnectionFactory createStandaloneConnectionFactory(
            ClientResources clientResources,
            GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig) {

        log.info("Configuring Redis standalone connection for {}:{} database:{}",
                redisHost, redisPort, redisDatabase);

        // Redis server configuration
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);
        redisConfig.setPort(redisPort);
        redisConfig.setDatabase(redisDatabase);

        // Set password if provided
        if (redisPassword != null && !redisPassword.trim().isEmpty()) {
            redisConfig.setPassword(redisPassword);
            log.info("Redis password authentication configured (length: {} chars)", redisPassword.length());
        } else {
            log.warn("Redis password is NOT configured - authentication disabled");
        }

        LettuceClientConfiguration clientConfig = buildClientConfiguration(clientResources, poolConfig);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig, clientConfig);
        factory.setValidateConnection(false);
        factory.afterPropertiesSet();

        return factory;
    }

    private LettuceConnectionFactory createSentinelConnectionFactory(
            ClientResources clientResources,
            GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig) {

        log.info("Configuring Redis Sentinel connection - Master: {}, Nodes: {}",
                sentinelMaster, sentinelNodes);

        // Validate required sentinel configuration
        if (sentinelMaster == null || sentinelMaster.trim().isEmpty()) {
            throw new IllegalArgumentException("Sentinel master name must be configured when sentinel mode is enabled");
        }

        if (sentinelNodes == null || sentinelNodes.trim().isEmpty()) {
            throw new IllegalArgumentException("Sentinel nodes must be configured when sentinel mode is enabled");
        }

        // Parse and validate Sentinel nodes
        String[] nodeArray = sentinelNodes.split(",");
        log.info("Parsed {} Sentinel nodes:", nodeArray.length);
        for (String node : nodeArray) {
            log.info(" - {}", node.trim());
        }

        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration(
                sentinelMaster,
                new HashSet<>(Arrays.asList(nodeArray))
        );

        // Set Redis data password (for master/slave connections)
        if (redisPassword != null && !redisPassword.trim().isEmpty()) {
            sentinelConfig.setPassword(redisPassword);
            log.info("Redis data password configured for master/slave connections (length: {} chars)",
                    redisPassword.length());
        } else {
            log.warn("Redis data password is NOT set - connection to master may fail if auth is required!");
        }

        // Set Sentinel password (for sentinel node connections)
        if (sentinelPassword != null && !sentinelPassword.trim().isEmpty()) {
            sentinelConfig.setSentinelPassword(sentinelPassword);
            log.info("Redis Sentinel password configured for sentinel node connections (length: {} chars)",
                    sentinelPassword.length());
        } else {
            log.warn("Sentinel password is NOT set - connection to Sentinel nodes may fail if auth is required!");
        }

        sentinelConfig.setDatabase(redisDatabase);
        log.info("Redis database configured: {}", redisDatabase);

        LettuceClientConfiguration clientConfig = buildClientConfiguration(clientResources, poolConfig);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(sentinelConfig, clientConfig);
        factory.setValidateConnection(false);
        factory.afterPropertiesSet();

        return factory;
    }

    private LettuceClientConfiguration buildClientConfiguration(
            ClientResources clientResources,
            GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig) {

        log.info("Configuring Lettuce client with connection pooling");
        log.info("Pool settings: maxTotal={}, maxIdle={}, minIdle={}",
                poolConfig.getMaxTotal(), poolConfig.getMaxIdle(), poolConfig.getMinIdle());

        // FIXED: Using proper type for poolConfig
        LettucePoolingClientConfiguration.LettucePoolingClientConfigurationBuilder builder =
                LettucePoolingClientConfiguration.builder()
                        .poolConfig(poolConfig)  // Now the types match!
                        .commandTimeout(Duration.ofMillis(redisTimeoutMs))
                        .shutdownTimeout(Duration.ofMillis(shutdownTimeoutMs))
                        .clientResources(clientResources)
                        .clientOptions(ClientOptions.builder()
                                .autoReconnect(true)
                                .pingBeforeActivateConnection(false)
                                .requestQueueSize(256)
                                .publishOnScheduler(true)
                                .build());

        // TLS / SSL configuration
        if (sslEnabled) {
            log.info("Enabling TLS/SSL for Redis connections");
            builder.useSsl();
        }

        log.info("Lettuce pooling client configured - connections will be reused and pooled");

        return builder.build();
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {

        log.info("Configuring primary RedisTemplate with JSON serialization");

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Configure ObjectMapper for Redis serialization with security modules
        ObjectMapper redisObjectMapper = objectMapper.copy();
        redisObjectMapper.registerModules(SecurityJackson2Modules.getModules(this.getClass().getClassLoader()));
        redisObjectMapper.findAndRegisterModules();

        // Serializers
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        // Apply serialization settings
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        // Enable transaction support for session management
        template.setEnableTransactionSupport(false); // Redis transactions are limited

        template.afterPropertiesSet();

        log.info("RedisTemplate configured with JSON serialization and security modules");
        return template;
    }

    @Bean("sessionRedisTemplate")
    public RedisTemplate<String, Object> sessionRedisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {

        log.info("Configuring session-specific RedisTemplate");

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Configure ObjectMapper specifically for session data
        ObjectMapper sessionObjectMapper = objectMapper.copy();
        sessionObjectMapper.registerModules(SecurityJackson2Modules.getModules(this.getClass().getClassLoader()));
        sessionObjectMapper.findAndRegisterModules();

        // Use same serializers as primary template for consistency
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(sessionObjectMapper);

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();

        log.info("Session RedisTemplate configured successfully");
        return template;
    }

    @Bean("cacheRedisTemplate")
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    public RedisTemplate<String, Object> cacheRedisTemplate(
            ClientResources clientResources,
            GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig) {

        log.info("Configuring dedicated RedisTemplate for distributed caching");

        // Create separate connection factory for cache
        LettuceConnectionFactory cacheConnectionFactory = createCacheConnectionFactory(clientResources, poolConfig);

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(cacheConnectionFactory);

        // Configure ObjectMapper specifically for cache data
        ObjectMapper cacheObjectMapper = new ObjectMapper();
        cacheObjectMapper.findAndRegisterModules();
        cacheObjectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        cacheObjectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // Use optimized serializers for cache performance
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(cacheObjectMapper);

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        // Enable transaction support for cache consistency
        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();

        log.info("Cache RedisTemplate configured successfully (DB {})", cacheDatabase);
        return template;
    }

    private LettuceConnectionFactory createCacheConnectionFactory(
            ClientResources clientResources,
            GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig) {

        log.info("Creating dedicated cache connection factory for database {}", cacheDatabase);

        LettuceConnectionFactory factory;

        if (sentinelEnabled) {
            log.info("Configuring cache connection factory in SENTINEL mode");

            // Validate required sentinel configuration
            if (sentinelMaster == null || sentinelMaster.trim().isEmpty()) {
                throw new IllegalArgumentException("Sentinel master name must be configured for cache connection");
            }

            if (sentinelNodes == null || sentinelNodes.trim().isEmpty()) {
                throw new IllegalArgumentException("Sentinel nodes must be configured for cache connection");
            }

            // Parse Sentinel nodes
            String[] nodeArray = sentinelNodes.split(",");
            RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration(
                    sentinelMaster,
                    new HashSet<>(Arrays.asList(nodeArray))
            );

            if (redisPassword != null && !redisPassword.trim().isEmpty()) {
                sentinelConfig.setPassword(redisPassword);
            }

            if (sentinelPassword != null && !sentinelPassword.trim().isEmpty()) {
                sentinelConfig.setSentinelPassword(sentinelPassword);
            }

            sentinelConfig.setDatabase(cacheDatabase);  // Use cache database

            LettuceClientConfiguration clientConfig = buildClientConfiguration(clientResources, poolConfig);
            factory = new LettuceConnectionFactory(sentinelConfig, clientConfig);

        } else {
            log.info("Configuring cache connection factory in STANDALONE mode");

            RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
            redisConfig.setHostName(redisHost);
            redisConfig.setPort(redisPort);
            redisConfig.setDatabase(cacheDatabase);  // Use cache database

            if (redisPassword != null && !redisPassword.trim().isEmpty()) {
                redisConfig.setPassword(redisPassword);
            }

            LettuceClientConfiguration clientConfig = buildClientConfiguration(clientResources, poolConfig);
            factory = new LettuceConnectionFactory(redisConfig, clientConfig);
        }

        factory.setValidateConnection(false);
        factory.afterPropertiesSet();

        log.info("Cache connection factory created successfully for database {}", cacheDatabase);
        return factory;
    }

    @Bean
    @ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
    public String redisHealthCheck(RedisTemplate<String, Object> redisTemplate) {
        log.info("--------------------------------------------------");
        log.info("Starting Redis Health Check");
        log.info("Health check mode: {}", healthCheckFailFast ? "FAIL-FAST (app will crash on error)" : "NON-FATAL (app will start with warnings)");

        try {
            // Log Lettuce version to verify upgrade
            String lettuceVersion = io.lettuce.core.LettuceVersion.getVersion();
            log.info("Lettuce Redis Driver Version: {}", lettuceVersion);

            var connectionFactory = redisTemplate.getConnectionFactory();
            if (connectionFactory == null) {
                log.error("Redis connection factory is null - configuration error!");
                if (healthCheckFailFast) {
                    throw new IllegalStateException("Redis connection factory is not configured");
                } else {
                    log.warn("Application will start without Redis (health check is non-fatal)");
                    return "Redis connection factory not configured - NON-FATAL MODE";
                }
            }

            log.info("Testing connection to Redis...");
            log.info("Sentinel mode: {}", sentinelEnabled);

            if (sentinelEnabled) {
                log.info("Sentinel master: {}", sentinelMaster);
                log.info("Sentinel nodes: {}", sentinelNodes);
            } else {
                log.info("Redis host: {}", redisHost);
                log.info("Redis port: {}", redisPort);
            }

            log.info("Database: {}", redisDatabase);
            log.info("Timeout: {}ms", redisTimeoutMs);

            // Perform PING test
            long startTime = System.currentTimeMillis();

            try (var connection = connectionFactory.getConnection()) {
                String pingResult = connection.ping();
                long duration = System.currentTimeMillis() - startTime;

                log.info("Redis PING successful! Response: {} (took {}ms)", pingResult, duration);

            } catch (RedisConnectionFailureException e) {
                long failTime = System.currentTimeMillis() - startTime;
                log.error("Redis connection FAILED after {}ms", failTime);
                log.error("Error: {}", e.getMessage());

                // Log full exception cause chain
                Throwable cause = e.getCause();
                int level = 1;
                while (cause != null) {
                    log.error("Caused by [Level {}]: {} - {}", level, cause.getClass().getSimpleName(), cause.getMessage());
                    cause = cause.getCause();
                    level++;
                }

                throw e;
            }

            // Test basic operations
            try {
                log.info("Testing basic SET/GET operations...");
                String testKey = "health:check:test";
                String testValue = "ok-" + System.currentTimeMillis();

                redisTemplate.opsForValue().set(testKey, testValue);
                Object retrieved = redisTemplate.opsForValue().get(testKey);
                redisTemplate.delete(testKey);

                if (testValue.equals(retrieved)) {
                    log.info("SET/GET operations working correctly");
                } else {
                    log.warn("SET/GET returned unexpected value: expected={}, got={}", testValue, retrieved);
                }

            } catch (Exception opEx) {
                log.warn("Basic operations test failed (non-fatal): {}", opEx.getMessage());
            }

            log.info("--------------------------------------------------");
            log.info("Redis Health Check PASSED - Connection is healthy!");
            log.info("--------------------------------------------------");

            return "Redis connection healthy";

        } catch (RedisConnectionException e) {
            return handleRedisConnectionError(e);
        } catch (Exception e) {
            return handleUnexpectedError(e);
        }
    }

    /**
     * Handle Redis connection errors with detailed diagnostics
     */
    private String handleRedisConnectionError(RedisConnectionException e) {
        log.error("--------------------------------------------------");
        log.error("❌ Redis Connection Error - Cannot establish connection!");
        log.error("--------------------------------------------------");
        log.error("Error type: RedisConnectionException");
        log.error("Error message: {}", e.getMessage());

        // Provide specific diagnostic guidance based on error message
        if (e.getMessage() != null) {
            if (e.getMessage().contains("NOAUTH") || e.getMessage().contains("authentication")) {
                log.error("🔐 AUTHENTICATION ERROR:");
                log.error("The Redis server requires authentication but the password may be incorrect or missing");
                log.error("Check environment variables:");
                log.error(" - SPRING_DATA_REDIS_PASSWORD (for master/slave connections)");
                log.error(" - SPRING_DATA_REDIS_SENTINEL_PASSWORD (for sentinel connections)");

            } else if (e.getMessage().contains("Cannot provide redisAddress") || e.getMessage().contains("sentinel")) {
                log.error("🛰️ SENTINEL DISCOVERY ERROR:");
                log.error("Cannot discover Redis master '{}' from Sentinel nodes", sentinelMaster);
                log.error("Attempted Sentinel nodes: {}", sentinelNodes);
                log.error("");
                log.error("This usually means:");
                log.error(" - Sentinel nodes are unreachable (DNS or network issue) < MOST LIKELY>");
                log.error(" - Sentinel services are not running");
                log.error(" - Network policies blocking traffic to Sentinel");
                log.error(" - Wrong Sentinel hostnames/IPs configured");
                log.error("");
                log.error("🔍 DEBUG STEPS - Exec into pod and run:");
                log.error("kubectl exec -it {} -n {} -- bash",
                        System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "POD_NAME",
                        System.getenv("POD_NAMESPACE") != null ? System.getenv("POD_NAMESPACE") : "NAMESPACE");
                log.error("");
                log.error("Then inside the pod:");
                log.error("1. Test DNS:      nslookup qwtsredis-auth01");
                log.error("2. Test network:  nc -zv qwtsredis-auth01 26379");
                log.error("3. Test Sentinel: redis-cli -h qwtsredis-auth01 -p 26379 PING");

                if (sentinelPassword != null && !sentinelPassword.trim().isEmpty()) {
                    log.error("4. Test with auth: redis-cli -h qwtsredis-auth01 -p 26379 -a 'YOUR_PASSWORD' PING");
                }

                log.error("");
                log.error("💡 QUICK FIXES:");
                log.error(" - Try FQDN: qwtsredis-auth01.<namespace>.svc.cluster.local:26379");
                log.error(" - Or use IPs: 10.x.x.x:26379 (get IPs with: kubectl get endpoints)");

            } else if (e.getMessage().contains("Connection refused") || e.getMessage().contains("refused")) {
                log.error("🚫 CONNECTION REFUSED:");
                log.error("Cannot connect to Redis server - check if:");
                log.error(" - Redis/Sentinel services are running");
                log.error(" - Network policies allow traffic");
                log.error(" - Firewall rules are correct");
                log.error(" - Host/port configuration is correct");

            } else if (e.getMessage().contains("timeout") || e.getMessage().contains("timed out")) {
                log.error("⏱️ CONNECTION TIMEOUT:");
                log.error("Redis server is not responding within {}ms", redisTimeoutMs);
                log.error("Check network latency and server health");
            }
        }

        log.error("Stack trace:", e);

        if (healthCheckFailFast) {
            log.error("--------------------------------------------------");
            log.error("❌ FAIL-FAST MODE: Application startup will be aborted!");
            log.error("--------------------------------------------------");
            throw new IllegalStateException("Failed to connect to Redis server - Check logs above", e);
        } else {
            log.warn("--------------------------------------------------");
            log.warn("⚠️ NON-FATAL MODE: Application will start WITHOUT Redis!");
            log.warn("⚠️ Redis-dependent features (sessions, caching) will NOT work!");
            log.warn("⚠️ This mode is for INVESTIGATION ONLY - not for production!");
            log.warn("--------------------------------------------------");
            return "Redis connection failed - NON-FATAL MODE - App started for investigation";
        }
    }

    /**
     * Handle unexpected errors during health check
     */
    private String handleUnexpectedError(Exception e) {
        log.error("--------------------------------------------------");
        log.error("❌ Redis Health Check FAILED with unexpected error!");
        log.error("--------------------------------------------------");
        log.error("Error type: {}", e.getClass().getSimpleName());
        log.error("Error message: {}", e.getMessage());
        log.error("Stack trace:", e);

        if (healthCheckFailFast) {
            log.error("--------------------------------------------------");
            log.error("❌ FAIL-FAST MODE: Application startup will be aborted!");
            log.error("--------------------------------------------------");
            throw new IllegalStateException("Failed to connect to Redis server", e);
        } else {
            log.warn("--------------------------------------------------");
            log.warn("⚠️ NON-FATAL MODE: Application will start WITHOUT Redis!");
            log.warn("⚠️ Redis-dependent features (sessions, caching) will NOT work!");
            log.warn("⚠️ This mode is for INVESTIGATION ONLY - not for production!");
            log.warn("--------------------------------------------------");
            return "Redis connection failed - NON-FATAL MODE - App started for investigation";
        }
    }
}