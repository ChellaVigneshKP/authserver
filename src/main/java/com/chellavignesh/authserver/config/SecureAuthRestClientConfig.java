package com.chellavignesh.authserver.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@Slf4j
public class SecureAuthRestClientConfig {

    @Value("${secureauth.connect-timeout:3000}")
    private int connectTimeoutMs;

    @Value("${secureauth.read-timeout:3000}")
    private int readTimeoutMs;

    @Bean("secureAuthRestClientBuilder")
    public RestClient.Builder secureAuthRestClientBuilder() {
        log.info("🚀 Configuring SecureAuth RestClient.Builder with performance optimizations");
        log.info("🔌 Connect timeout: {}ms", connectTimeoutMs);
        log.info("⏱️ Read timeout: {}ms", readTimeoutMs);

        // FIX #27: Create ClientHttpRequestFactory with timeouts
        // This uses Apache HttpClient under the hood with connection pooling
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));

        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

        log.info("🧩 SecureAuth RestClient.Builder configured with timeouts");
        log.warn("⚠️ SecureAuth MFA requests will timeout after {}ms read timeout", readTimeoutMs);

        return RestClient.builder()
                .requestFactory(requestFactory);
    }

    /**
     * Logs configuration on startup for verification
     */
    @PostConstruct
    public void logConfiguration() {
        log.debug("=== SecureAuth RestClient Configuration ===");
        log.debug("🔌 Connect timeout: {}ms (how long to wait for connection)", connectTimeoutMs);
        log.debug("⏱️ Read timeout: {}ms (how long to wait for response)", readTimeoutMs);
        log.debug("📦 Backend: Apache HttpClient connection pooling");
        log.debug("===========================================");
    }
}
