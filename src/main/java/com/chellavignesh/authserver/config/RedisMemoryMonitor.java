package com.chellavignesh.authserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Properties;

@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
public class RedisMemoryMonitor {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final double MEMORY_WARNING_THRESHOLD = 0.70; // 70%
    private static final double MEMORY_CRITICAL_THRESHOLD = 0.85; // 85%
    private static final double MEMORY_EMERGENCY_THRESHOLD = 0.95; // 95%

    // FIX #28: Minimum memory threshold to avoid false fragmentation warnings
    private static final long FRAGMENTATION_MIN_MEMORY_BYTES = 100_000_000; // 100MB

    public RedisMemoryMonitor(@Qualifier("sessionRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedRate = 120000) // Every 2 minutes
    public void monitorRedisMemory() {
        try {
            Properties info = redisTemplate.execute((RedisCallback<Properties>) connection -> connection.info("memory"));

            if (info == null) {
                log.warn("▲ [REDIS-MEMORY] Could not retrieve Redis memory info");
                return;
            }

            // Parse memory statistics
            long usedMemory = parseLong(info.getProperty("used_memory", "0"));
            long maxMemory = parseLong(info.getProperty("maxmemory", "0"));
            long usedMemoryRss = parseLong(info.getProperty("used_memory_rss", "0"));
            long usedMemoryPeak = parseLong(info.getProperty("used_memory_peak", "0"));
            String evictionPolicy = info.getProperty("maxmemory_policy", "unknown");
            long evictedKeys = parseLong(info.getProperty("evicted_keys", "0"));

            // Calculate utilization
            double memoryUtilization = maxMemory > 0 ? (double) usedMemory / maxMemory : 0;
            double rssUtilization = maxMemory > 0 ? (double) usedMemoryRss / maxMemory : 0;

            // Format for readability
            String usedMB = formatBytes(usedMemory);
            String maxMB = formatBytes(maxMemory);
            String rssMB = formatBytes(usedMemoryRss);
            String peakMB = formatBytes(usedMemoryPeak);

            // Determine status and log appropriately
            if (memoryUtilization >= MEMORY_EMERGENCY_THRESHOLD) {
                log.error("[REDIS-MEMORY-EMERGENCY] CRITICAL: Redis memory at {}% capacity!", String.format("%.1f", memoryUtilization * 100));
                log.error("Used: {} / Max: {} | RSS: {} | Peak: {}", usedMB, maxMB, rssMB, peakMB);
                log.error("Policy: {} | Evicted keys: {}", evictionPolicy, evictedKeys);
                log.error("IMMEDIATE ACTION REQUIRED: Increase MAXMEMORY or scale Redis!");
                log.error("Active sessions and cache entries are being evicted!");

            } else if (memoryUtilization >= MEMORY_CRITICAL_THRESHOLD) {
                log.error("●● [REDIS-MEMORY-CRITICAL] Redis memory at {}% capacity", String.format("%.1f", memoryUtilization * 100));
                log.error("...Used: {} / Max: {} | RSS: {} | Peak: {}", usedMB, maxMB, rssMB, peakMB);
                log.error("...Policy: {} | Evicted keys: {}", evictionPolicy, evictedKeys);
                log.error("▲ Action required: Plan to increase MAXMEMORY soon");

            } else if (memoryUtilization >= MEMORY_WARNING_THRESHOLD) {
                log.warn("[REDIS-MEMORY-WARNING] Redis memory at {}% capacity", String.format("%.1f", memoryUtilization * 100));
                log.warn("Used: {} / Max: {} | RSS: {} | Peak: {}", usedMB, maxMB, rssMB, peakMB);
                log.warn("Policy: {} | Evicted keys: {}", evictionPolicy, evictedKeys);

            } else {
                log.debug("[REDIS-MEMORY] Healthy - {}% used", String.format("%.1f", memoryUtilization * 100));
            }

            if (evictedKeys > 0) {
                log.warn("[REDIS-MEMORY] {} keys have been evicted! Consider increasing MAXMEMORY", evictedKeys);
            }

            // FIX #28: Only warn about fragmentation if memory usage is meaningful
            if (rssUtilization > memoryUtilization * 1.5) {
                if (usedMemory > FRAGMENTATION_MIN_MEMORY_BYTES) {
                    // Real fragmentation concern - meaningful memory is being wasted
                    log.warn("▲ [REDIS-MEMORY] High memory fragmentation detected!");
                    log.warn(" Used: {} | RSS: {} ({}% overhead)", usedMB, rssMB, (int) ((rssUtilization / memoryUtilization - 1) * 100));
                    log.warn(" Consider restarting Redis during maintenance window");
                } else {
                    // Low absolute memory - fragmentation ratio is misleading
                    log.debug("[REDIS-MEMORY] Fragmentation detected but memory usage too low to matter");
                    log.debug(" Used: {} | RSS: {} | This is normal Redis base overhead (< {}MB data)", usedMB, rssMB, FRAGMENTATION_MIN_MEMORY_BYTES / 1_000_000);
                }
            }

        } catch (Exception e) {
            log.warn("[REDIS-MEMORY] Failed to monitor Redis memory: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 600000) // Every 10 minutes
    public void logDetailedMemoryStats() {
        try {
            Properties info = redisTemplate.execute((RedisCallback<Properties>) connection -> connection.info("memory"));

            if (info == null) return;

            log.info("[REDIS-MEMORY-DETAILED] Memory Statistics:");
            log.info(" Used Memory: {}", formatBytes(parseLong(info.getProperty("used_memory", "0"))));
            log.info(" Used Memory Human: {}", info.getProperty("used_memory_human", "N/A"));
            log.info(" Used Memory RSS: {}", formatBytes(parseLong(info.getProperty("used_memory_rss", "0"))));
            log.info(" Used Memory Peak: {}", formatBytes(parseLong(info.getProperty("used_memory_peak", "0"))));
            log.info(" Max Memory: {}", formatBytes(parseLong(info.getProperty("maxmemory", "0"))));
            log.info(" Max Memory Human: {}", info.getProperty("maxmemory_human", "N/A"));
            log.info(" Max Memory Policy: {}", info.getProperty("maxmemory_policy", "N/A"));
            log.info(" Evicted Keys (Lifetime): {}", info.getProperty("evicted_keys", "0"));
            log.info(" Memory Fragmentation Ratio: {}", info.getProperty("mem_fragmentation_ratio", "N/A"));

        } catch (Exception e) {
            log.debug("Could not retrieve detailed Redis memory stats: {}", e.getMessage());
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException _) {
            return 0;
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.2fKB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2fMB", bytes / (1024.0 * 1024));
        return String.format("%.2fGB", bytes / (1024.0 * 1024 * 1024));
    }
}
