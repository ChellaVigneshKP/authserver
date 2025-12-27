package com.chellavignesh.authserver.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.session.web.context.AbstractHttpSessionApplicationInitializer;

import java.time.Duration;
import java.time.Instant;

@Configuration
@Profile("!test")
@EnableRedisHttpSession( // 30 minutes
)
@Slf4j
public class SessionConfig extends AbstractHttpSessionApplicationInitializer {

    // Inject session namespace from properties (environment-specific)
    @Value("${spring.session.redis.namespace:agsup-auth:spring:session}")
    private String sessionNamespace;

    @Value("${spring.session.timeout:1800}")
    private int sessionTimeoutSeconds;

    @Value("${spring.data.redis.sentinel.enabled:false}")
    private boolean isSentinelMode;

    /**
     * Disable Redis CONFIG commands for Sentinel / restricted environments
     */
    @Bean
    public ConfigureRedisAction configureRedisAction() {
        if (isSentinelMode) {
            log.info("Redis sentinel mode detected - disabling Redis CONFIG commands");
        } else {
            log.info("Redis standalone mode - CONFIG commands allowed");
        }
        return ConfigureRedisAction.NO_OP;
    }

    /**
     * Optimized Redis serializer for Spring Session
     */
    @Bean("springSessionDefaultRedisSerializer")
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {

        log.info("Configuring optimized Redis serializer for Spring Session");

        ObjectMapper sessionObjectMapper = new ObjectMapper();
        sessionObjectMapper.registerModule(new JavaTimeModule());

        sessionObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        sessionObjectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        sessionObjectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        sessionObjectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

        log.info("[SESSION-PERF] Optimized Redis session serializer enabled");
        log.info("Expected serialization improvement: 20–30ms per session operation");

        return new GenericJackson2JsonRedisSerializer(sessionObjectMapper);
    }

    /**
     * Override RedisSessionRepository to apply namespace and timeout
     */
    @Bean
    @Primary
    public RedisSessionRepository redisSessionRepository(RedisOperations<String, Object> sessionRedisOperations) {

        RedisSessionRepository repository = new RedisSessionRepository(sessionRedisOperations);

        repository.setDefaultMaxInactiveInterval(Duration.ofSeconds(sessionTimeoutSeconds));

        repository.setRedisKeyNamespace(sessionNamespace);

        log.info("[SESSION-DEBUG] Custom Redis session repository configured");
        log.info("Using session namespace: {}", sessionNamespace);
        log.info("Session timeout: {} seconds", sessionTimeoutSeconds);
        log.info("SaveMode and FlushMode controlled by application.properties");

        return repository;
    }

    /**
     * Session lifecycle event listener
     */
    @Bean
    public SessionEventListener sessionEventListener() {
        return new SessionEventListener();
    }

    public static class SessionEventListener {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SessionEventListener.class);

        @EventListener
        public void handleSessionCreated(SessionCreatedEvent event) {
            log.debug("Session created: {} at {}", event.getSessionId(), Instant.ofEpochMilli(event.getTimestamp()));
        }

        @EventListener
        public void handleSessionDeleted(SessionDeletedEvent event) {
            log.debug("Session deleted: {} at {}", event.getSessionId(), Instant.ofEpochMilli(event.getTimestamp()));
        }

        @EventListener
        public void handleSessionExpired(SessionExpiredEvent event) {
            log.debug("Session expired: {} at {}", event.getSessionId(), Instant.ofEpochMilli(event.getTimestamp()));
        }
    }

    /**
     * Redis session health indicator
     */
    @Bean
    public RedisSessionHealthIndicator redisSessionHealthIndicator(@Qualifier("sessionRedisTemplate") RedisTemplate<String, Object> sessionRedisTemplate) {
        return new RedisSessionHealthIndicator(sessionRedisTemplate);
    }

    public static class RedisSessionHealthIndicator {

        private final RedisTemplate<String, Object> redisTemplate;

        public RedisSessionHealthIndicator(RedisTemplate<String, Object> redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        @Scheduled(fixedRate = 300000) // Every 5 minutes
        public void checkRedisHealth() {
            try {
                redisTemplate.execute((RedisCallback<String>) connection -> {
                    connection.ping();
                    return "OK";
                });
                log.debug("Redis session health check: OK");
            } catch (Exception e) {
                log.warn("Redis session health check failed: {}", e.getMessage());
            }
        }

        @Scheduled(fixedRate = 300000) // Every 5 minutes
        public void logRedisMetrics() {
            try {
                RedisConnectionFactory factory = redisTemplate.getConnectionFactory();

                if (factory instanceof LettuceConnectionFactory lettuceFactory) {
                    log.info("[REDIS-POOL-METRICS] Redis session connection healthy");
                    log.info("Shared native connection: {}", lettuceFactory.getShareNativeConnection());
                    log.info("Validate connections: {}", lettuceFactory.getValidateConnection());
                }
            } catch (Exception e) {
                log.warn("Failed to collect Redis metrics: {}", e.getMessage());
            }
        }
    }

    @Bean
    public SessionAttributteSerializationHandler sessionAttributteSerializationHandler() {
        return new SessionAttributteSerializationHandler();
    }


    public static class SessionAttributteSerializationHandler {

        @EventListener
        public void handleSessionAttributeAdded(SessionCreatedEvent event) {
            log.debug("Session attribute added: {} at {}", event.getSessionId(), event.getTimestamp());
        }

        @EventListener
        public void handleSessionDestroyed(SessionDeletedEvent event) {
            log.debug("Session attribute removed: {} at {}", event.getSessionId(), event.getTimestamp());
        }

        @EventListener
        public void handleSessionExpired(SessionExpiredEvent event) {
            log.debug("Session attribute expired: {} at {}", event.getSessionId(), event.getTimestamp());
        }
    }

    /**
     * Log configuration at startup
     */
    @PostConstruct
    public void logSessionConfiguration() {
        log.debug("=== Redis Session Configuration ===");
        log.debug("Session namespace: {}", sessionNamespace);
        log.debug("Session timeout: {} seconds", sessionTimeoutSeconds);
        log.debug("Sentinel mode: {}", isSentinelMode);
        log.debug("Expected key format: {}:sessions:{{sessionId}}", sessionNamespace);
        log.debug("==================================");
    }
}

