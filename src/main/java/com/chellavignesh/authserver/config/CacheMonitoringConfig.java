package com.chellavignesh.authserver.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
@Slf4j
public class CacheMonitoringConfig {

    private final CacheManager cacheManager;

    public CacheMonitoringConfig(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Log cache statistics every 10 minutes for monitoring.
     */
    @Scheduled(fixedRate = 600000) // 10 minutes
    public void logCacheMetrics() {

        if (cacheManager instanceof RedisCacheManager) {
            try {
                // Create defensive copy to avoid ConcurrentModificationException during load testing
                Collection<String> cacheNames = new ArrayList<>(cacheManager.getCacheNames());

                log.info("📊 [CACHE-METRICS] Redis distributed cache active");
                log.info("🔎 [CACHE-METRICS] Available caches: {}", cacheNames);
                log.info("📦 [CACHE-METRICS] Cache type: Redis (distributed across pods)");

            } catch (ConcurrentModificationException ignored) {
                log.warn("⚠️ [CACHE-METRICS] Could not retrieve cache names " + "(concurrent modification during load test)");
            }
        }
    }

    /**
     * Log cache configuration on startup.
     */
    @PostConstruct
    public void logCacheConfiguration() {

        try {
            // Create defensive copy to avoid ConcurrentModificationException
            Collection<String> cacheNames = new ArrayList<>(cacheManager.getCacheNames());

            log.debug("=== Distributed Cache Configuration ===");
            log.debug("Cache Manager: {}", cacheManager.getClass().getSimpleName());
            log.debug("Cache Names: {}", cacheNames);

            if (cacheManager instanceof RedisCacheManager) {
                log.debug("✅ Redis distributed cache ENABLED - eliminates redundant DB calls across pods");
            }

            log.debug("=======================================");

        } catch (ConcurrentModificationException ignored) {
            log.warn("⚠️ Could not retrieve cache names at startup (concurrent modification)");
        }
    }
}

