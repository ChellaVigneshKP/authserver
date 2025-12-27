package com.chellavignesh.authserver.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class RedisOptimizationConfig {

    /**
     * Optimized ClientResources for Lettuce Redis client.
     * Configures thread pools and I/O settings for optimal performance.
     */
    @Bean(destroyMethod = "shutdown")
    public ClientResources lettuceClientResources() {

        // Optimize I/O thread pool size based on CPU cores
        int ioThreadPoolSize = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);

        // Optimize computation thread pool size
        int computationThreadPoolSize = Math.max(2, Runtime.getRuntime().availableProcessors());

        ClientResources resources = DefaultClientResources.builder()
                .ioThreadPoolSize(ioThreadPoolSize)
                .computationThreadPoolSize(computationThreadPoolSize)
                .build();

        log.info("✅ [REDIS-OPTIMIZATION] Lettuce ClientResources configured");
        log.info("   - I/O threads: {} (CPU cores / 2)", ioThreadPoolSize);
        log.info("   - Computation threads: {} (CPU cores)", computationThreadPoolSize);

        return resources;
    }

    /**
     * Configures timeouts, socket options, and protocol settings.
     */
    @Bean
    public ClientOptions lettuceClientOptions() {

        SocketOptions socketOptions = SocketOptions.builder()
                .keepAlive(true)
                .tcpNoDelay(true)
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        TimeoutOptions timeoutOptions = TimeoutOptions.builder()
                .fixedTimeout(Duration.ofMillis(1000))
                .timeoutCommands(true)
                .build();

        ClientOptions options = ClientOptions.builder()
                .socketOptions(socketOptions)
                .timeoutOptions(timeoutOptions)
                .autoReconnect(true)
                .suspendReconnectOnProtocolFailure(true)
                .requestQueueSize(65536)
                .protocolVersion(ProtocolVersion.RESP3)
                .publishOnScheduler(true)
                .build();

        log.info("✅ [REDIS-OPTIMIZATION] Lettuce ClientOptions configured");
        log.info("   - TCP_NODELAY: enabled (lower latency)");
        log.info("   - TCP KeepAlive: enabled (detect dead connections)");
        log.info("   - Command timeout: 1000ms (fast failover)");
        log.info("   - Auto-reconnect: enabled with backoff");
        log.info("   - Protocol: RESP3 (with RESP2 fallback)");

        return options;
    }
}

