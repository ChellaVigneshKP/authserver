package com.chellavignesh.authserver.authorizedrestclient;

import com.chellavignesh.authjavasdk.AuthClient;
import com.chellavignesh.authjavasdk.AuthClientFactory;
import com.chellavignesh.authjavasdk.ClientCredentialJwtConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
public class AuthClientConfig {

    @Bean
    public HttpClient sharedHttpClient(
            @Value("${http.proxyHost:}") String httpProxyServer,
            @Value("${http.proxyPort:80}") String httpProxyPort,
            @Value("${http.nonProxyHosts:}") String nonProxyHosts
    ) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (httpProxyServer != null && !httpProxyServer.isBlank()) {
            ProxySelector proxySelector = createProxySelector(httpProxyServer, httpProxyPort, nonProxyHosts);
            builder.proxy(proxySelector);
            log.info("Proxy server='{}' port='{}' -> {}", httpProxyServer, httpProxyPort, proxySelector.select(null));
        } else {
            log.info("Proxy is NOT used");
        }
        return builder.build();
    }

    @Bean
    public HttpClient noProxyHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    AuthClient authClient(
            HttpClient sharedHttpClient,
            @Value("${ascensus.auth.server.root-url}") String rootUrl,
            @Value("${ascensus.auth.server.token-endpoint}") URI tokenEndpoint,
            @Value("${ascensus.auth.server.jwks-endpoint}") URI jwksEndpoint,
            @Value("${ascensus.auth.server.org-id}") String orgId,
            @Value("${ascensus.auth.server.client-id}") String clientId,
            @Value("${ascensus.auth.server.shared-secret}") String sharedSecret) {
        var config = new ClientCredentialJwtConfig(rootUrl, tokenEndpoint, jwksEndpoint, orgId, clientId, List.of("read"), sharedSecret);
        return AuthClientFactory.from(config, 1, sharedHttpClient);
    }

    @Bean
    AuthClient noProxyAuthClient(
            HttpClient noProxyHttpClient,
            @Value("${ascensus.auth.server.root-url}") String rootUrl,
            @Value("${ascensus.auth.server.token-endpoint}") URI tokenEndpoint,
            @Value("${ascensus.auth.server.jwks-endpoint}") URI jwksEndpoint,
            @Value("${ascensus.auth.server.org-id}") String orgId,
            @Value("${ascensus.auth.server.client-id}") String clientId,
            @Value("${ascensus.auth.server.shared-secret}") String sharedSecret) {

        var config = new ClientCredentialJwtConfig(rootUrl, tokenEndpoint, jwksEndpoint, orgId, clientId, List.of("read"), sharedSecret);
        return AuthClientFactory.from(config, 1, noProxyHttpClient);
    }

    @Bean
    AuthClient proxyAuthClient(
            HttpClient sharedHttpClient,
            @Value("${ascensus.auth.server.root-url}") String rootUrl,
            @Value("${ascensus.auth.server.token-endpoint}") URI tokenEndpoint,
            @Value("${ascensus.auth.server.jwks-endpoint}") URI jwksEndpoint,
            @Value("${ascensus.auth.server.org-id}") String orgId,
            @Value("${ascensus.auth.server.client-id}") String clientId,
            @Value("${ascensus.auth.server.shared-secret}") String sharedSecret) {
        // Use shared http client for proxy
        var config = new ClientCredentialJwtConfig(rootUrl, tokenEndpoint, jwksEndpoint, orgId, clientId, List.of("read"), sharedSecret);
        return AuthClientFactory.from(config, 1, sharedHttpClient);
    }

    private ProxySelector createProxySelector(String httpProxyServer, String httpProxyPort, String nonProxyHosts) {

        String domainName = httpProxyServer;
        String[] parts = httpProxyServer.split("//");
        if (parts.length >= 2) {
            domainName = parts[parts.length - 1];
        }
        String finalDomainName = domainName;

        int proxyPort = 80;
        if (httpProxyPort != null && !httpProxyPort.isBlank()) {
            proxyPort = Integer.parseInt(httpProxyPort);
        }
        int finalProxyPort = proxyPort;

        InetSocketAddress proxyAddress = new InetSocketAddress(finalDomainName, proxyPort);

        return new ProxySelector() {

            @Override
            public List<Proxy> select(URI uri) {
                log.debug("Proxy select uri: {}", uri);
                List<String> nonProxyHostsList = Arrays.asList(nonProxyHosts.replace("\"", "").split("\\|"));

                if (nonProxyHostsList.contains(uri.getHost())) {
                    log.debug("Proxy bypass for host: {}", uri.getHost());
                    return List.of(Proxy.NO_PROXY);
                } else {
                    log.debug("Using proxy: {}:{} for {}", finalDomainName, finalProxyPort, uri.getHost());
                    return List.of(new Proxy(Proxy.Type.HTTP, proxyAddress));
                }
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                log.error("Failed to connect to {} Reason: {}, {}", uri, sa, ioe.getMessage());
            }
        };
    }
}
