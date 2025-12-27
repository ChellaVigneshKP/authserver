package com.chellavignesh.authserver.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class LibCryptoWebClientConfig {

    @Value("${crypto.web.connect-timeout}")
    private int connectTimeoutMs;

    @Value("${crypto.web.read-timeout}")
    private int readTimeoutMs;

    @Value("${crypto.web.connection-pool.max-connections}")
    private int maxConnections;

    @Value("${crypto.web.connection-pool.max-per-route}")
    private int maxConnectionsPerRoute;

    @Bean
    public WebClient.Builder libCryptoWebClientBuilder() {

        log.info("🚀 Configuring lib-crypto WebClient.Builder with performance optimizations");
        log.info("Connect timeout: {}ms", connectTimeoutMs);
        log.info("Read timeout: {}ms", readTimeoutMs);
        log.info("Max connections: {}", maxConnections);
        log.info("Max per route: {}", maxConnectionsPerRoute);

        // FIX #15: Configure connection pool with limits to prevent resource exhaustion
        ConnectionProvider connectionProvider =
                ConnectionProvider.builder("lib-crypto-pool")
                        .maxConnections(maxConnections)
                        .maxIdleTime(Duration.ofSeconds(20))     // Close idle connections after 20s
                        .maxLifeTime(Duration.ofSeconds(60))     // Recreate connections every 60s
                        .pendingAcquireTimeout(Duration.ofMillis(connectTimeoutMs))
                        .evictInBackground(Duration.ofSeconds(30)) // Cleanup stale connections
                        .build();

        // FIX #15: Configure HttpClient with aggressive timeouts
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(readTimeoutMs))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS))
                );

        log.info("✅ lib-crypto WebClient.Builder configured with timeouts and connection pooling");
        log.warn("⚠️ Requests to crypto-web service will timeout after {}ms read timeout", readTimeoutMs);

        // Return configured WebClient.Builder
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    /**
     * Logs configuration on startup for verification
     */
    @PostConstruct
    public void logConfiguration() {
        log.debug("=== lib-crypto WebClient Configuration ===");
        log.debug("Connect timeout: {}ms (how long to wait for connection)", connectTimeoutMs);
        log.debug("Read timeout: {}ms (how long to wait for response)", readTimeoutMs);
        log.debug("Max connections: {} (connection pool size)", maxConnections);
        log.debug("Max per route: {} (connections per host)", maxConnectionsPerRoute);
        log.debug("==========================================");
    }
}

