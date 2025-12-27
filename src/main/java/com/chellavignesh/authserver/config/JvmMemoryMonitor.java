package com.chellavignesh.authserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.management.*;

import java.util.List;

@Configuration
@EnableScheduling
@Slf4j
public class JvmMemoryMonitor {

    private static final double HEAP_WARNING_THRESHOLD = 0.70; // 70%
    private static final double HEAP_CRITICAL_THRESHOLD = 0.85; // 85%
    private static final double HEAP_EMERGENCY_THRESHOLD = 0.95; // 95%

    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    /**
     * Monitor JVM heap memory every 2 minutes
     * Detects memory leaks and excessive GC pressure
     */
    @Scheduled(fixedRate = 120000) // Every 2 minutes
    public void monitorHeapMemory() {
        try {
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

            long heapUsed = heapUsage.getUsed();
            long heapMax = heapUsage.getMax();
            long heapCommitted = heapUsage.getCommitted();

            double heapUtilization = (double) heapUsed / heapMax;

            String usedMB = formatBytes(heapUsed);
            String maxMB = formatBytes(heapMax);
            String committedMB = formatBytes(heapCommitted);
            String nonHeapMB = formatBytes(nonHeapUsage.getUsed());

            if (heapUtilization >= HEAP_EMERGENCY_THRESHOLD) {
                log.error("🔴🔴🔴 [JVM-MEMORY-EMERGENCY] CRITICAL: Heap at {}% capacity!", String.format("%.1f", heapUtilization * 100));
                log.error("Heap: {} / {} (committed: {})", usedMB, maxMB, committedMB);
                log.error("Non-Heap: {}", nonHeapMB);
                log.error("⚠️ IMMEDIATE ACTION REQUIRED: Application may crash soon!");
                log.error("Heap dump or restart recommended");

            } else if (heapUtilization >= HEAP_CRITICAL_THRESHOLD) {
                log.error("🔴 [JVM-MEMORY-CRITICAL] Heap at {}% capacity", String.format("%.1f", heapUtilization * 100));
                log.error("Heap: {} / {} (committed: {})", usedMB, maxMB, committedMB);
                log.error("Non-Heap: {}", nonHeapMB);
                log.error("⚠️ Possible memory leak or insufficient heap size");

            } else if (heapUtilization >= HEAP_WARNING_THRESHOLD) {
                log.warn("🟡 [JVM-MEMORY-WARNING] Heap at {}% capacity", String.format("%.1f", heapUtilization * 100));
                log.warn("Heap: {} / {} (committed: {})", usedMB, maxMB, committedMB);
                log.warn("Non-Heap: {}", nonHeapMB);

            } else {
                log.debug("🟢 [JVM-MEMORY] Healthy - Heap {}% used", String.format("%.1f", heapUtilization * 100));
            }

            // Monitor thread count
            int threadCount = threadMXBean.getThreadCount();
            int peakThreadCount = threadMXBean.getPeakThreadCount();

            if (threadCount > 500) {
                log.warn("⚠️ [JVM-THREADS] High thread count: {} (peak: {})", threadCount, peakThreadCount);
                log.warn("Possible thread leak or connection pool exhaustion");
            } else {
                log.debug("[JVM-THREADS] Active: {} (peak: {})", threadCount, peakThreadCount);
            }

        } catch (Exception e) {
            log.warn("⚠️ [JVM-MEMORY] Failed to monitor JVM memory: {}", e.getMessage());
        }
    }

    /**
     * Monitor garbage collection behavior every 5 minutes
     * High GC activity indicates memory pressure or leaks
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void monitorGarbageCollection() {
        try {
            log.info("🧹 [JVM-GC] Garbage Collection Statistics:");

            long totalGcTime = 0;
            long totalGcCount = 0;

            for (GarbageCollectorMXBean gcBean : gcBeans) {
                String gcName = gcBean.getName();
                long gcCount = gcBean.getCollectionCount();
                long gcTime = gcBean.getCollectionTime();

                totalGcCount += gcCount;
                totalGcTime += gcTime;

                log.info("{}: {} collections, {} total time", gcName, gcCount, formatMillis(gcTime));

                if ((gcName.contains("Old") || gcName.contains("MarkSweep")) && gcTime > 60000) {
                    log.warn("⚠️ [JVM-GC] High Full GC time for {}: {}", gcName, formatMillis(gcTime));
                    log.warn("This indicates memory pressure or heap tuning needed");
                }
            }

            log.info("Total GC: {} collections, {} total time", totalGcCount, formatMillis(totalGcTime));

            if (totalGcTime > 300000) {
                log.warn("⚠️ [JVM-GC] Excessive total GC time: {}", formatMillis(totalGcTime));
                log.warn("Application spending too much time in garbage collection");
                log.warn("Consider increasing heap size or investigating memory leaks");
            }

        } catch (Exception e) {
            log.debug("Could not retrieve GC statistics: {}", e.getMessage());
        }
    }

    /**
     * Log detailed JVM memory statistics every 10 minutes
     */
    @Scheduled(fixedRate = 600000) // Every 10 minutes
    public void logDetailedMemoryStats() {
        try {
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

            log.info("📊 [JVM-MEMORY-DETAILED] Memory Statistics:");
            log.info("Heap Init: {}", formatBytes(heapUsage.getInit()));
            log.info("Heap Used: {}", formatBytes(heapUsage.getUsed()));
            log.info("Heap Committed: {}", formatBytes(heapUsage.getCommitted()));
            log.info("Heap Max: {}", formatBytes(heapUsage.getMax()));
            log.info("Non-Heap Used: {}", formatBytes(nonHeapUsage.getUsed()));
            log.info("Non-Heap Committed: {}", formatBytes(nonHeapUsage.getCommitted()));
            log.info("Threads: {} (peak: {}, total started: {})", threadMXBean.getThreadCount(), threadMXBean.getPeakThreadCount(), threadMXBean.getTotalStartedThreadCount());
            log.info("Loaded Classes: {}", ManagementFactory.getClassLoadingMXBean().getLoadedClassCount());

        } catch (Exception e) {
            log.debug("Could not retrieve detailed JVM memory stats: {}", e.getMessage());
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String formatMillis(long millis) {
        if (millis < 1000) return millis + " ms";
        if (millis < 60000) return String.format("%.2f s", millis / 1000.0);
        return String.format("%.2f min", millis / 60000.0);
    }
}

