package com.chellavignesh.authserver.config;


import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableScheduling
@EnableAsync
@EnableAspectJAutoProxy
@Slf4j
public class PerformanceMonitoringConfig implements AsyncConfigurer {

    private final MeterRegistry meterRegistry;

    public PerformanceMonitoringConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Configure async executor to prevent thread pool exhaustion
     * which can cause CPU spikes with confidential clients.
     */
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Optimize for confidential client processing
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("AuthServer-Async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // Add CPU monitoring
        executor.setRejectedExecutionHandler((Runnable runnable, java.util.concurrent.ThreadPoolExecutor threadPoolExecutor) -> {
            log.warn("Async task rejected - possible CPU overload. Active: {}, Pool: {}, Queue: {}", threadPoolExecutor.getActiveCount(), threadPoolExecutor.getPoolSize(), threadPoolExecutor.getQueue().size());
            meterRegistry.counter("authserver.async.tasks.rejected").increment();
        });

        executor.initialize();

        log.info("Async executor configured for CPU optimization: core={}, max={}, queue={}", executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler();
    }

    @Bean
    public ConfidentialClientMonitor confidentialClientMonitor() {
        return new ConfidentialClientMonitor(meterRegistry);
    }

    /**
     * Inner class for monitoring confidential client CPU usage.
     */
    public static class ConfidentialClientMonitor {

        private final MeterRegistry meterRegistry;

        public ConfidentialClientMonitor(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        @Timed(value = "authserver.confidential.client.processing", description = "Time spent processing confidential clients")
        public void recordConfidentialClientProcessing(String clientId, Runnable operation) {
            long startTime = System.currentTimeMillis();
            try {
                operation.run();
                meterRegistry.counter("authserver.confidential.client.success", "client", clientId).increment();
            } catch (Exception e) {
                meterRegistry.counter("authserver.confidential.client.error", "client", clientId, "error", e.getClass().getSimpleName()).increment();
                log.error("Error processing confidential client {}: {}", clientId, e.getMessage());
                throw e;
            } finally {
                long duration = System.currentTimeMillis() - startTime;

                // Log slow operations (> 1 second)
                if (duration > 1000) {
                    log.warn("Slow confidential client processing for {}: {}ms", clientId, duration);
                }

                meterRegistry.timer("authserver.confidential.client.duration", "client", clientId).record(duration, TimeUnit.MILLISECONDS);
            }
        }
    }
}

