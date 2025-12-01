package com.chellavignesh.authserver.unite;

import com.chellavignesh.authserver.unite.dto.BiometricTokenValidationDto;
import com.chellavignesh.authserver.unite.dto.UniteUserDto;
import com.chellavignesh.authserver.unite.exception.BiometricInvalidCredentialsException;
import com.chellavignesh.authserver.unite.exception.BiometricTokenValidationFailedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class UniteMSCServiceClient {
    private static final Logger log = LoggerFactory.getLogger(UniteMSCServiceClient.class);
    private static final String VALIDATE_BIOMETRIC_TOKEN_ENDPOINT =
            "/mobile1e/api/v1/mobilemembersession/validateBiometricToken";

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final URI uniteServiceUrl;
    private final HttpClient httpClient;

    public UniteMSCServiceClient(
            @Value("${ascensus.url.unite.mobile}") URI uniteServiceUrl,
            @Value("${http.proxyHost:}") String httpProxyServer,
            @Value("${http.proxyPort:80}") String httpProxyPort,
            @Value("${http.nonProxyHosts:}") String nonProxyHosts) {

        this.uniteServiceUrl = uniteServiceUrl;

        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL);

        if (httpProxyServer != null && !httpProxyServer.isBlank()) {
            ProxySelector proxySelector =
                    createProxySelector(httpProxyServer, httpProxyPort, nonProxyHosts);
            clientBuilder.proxy(proxySelector);
        }

        this.httpClient = clientBuilder.build();
    }

    @PreDestroy
    public void shutdown() {
        log.info("🛑 UniteMSCServiceClient shutting down - HttpClient resources will be released");
    }

    private ProxySelector createProxySelector(String httpProxyServer,
                                              String httpProxyPort,
                                              String nonProxyHosts) {

        String domainName = httpProxyServer;
        String[] parts = httpProxyServer.split("//");
        if (parts.length >= 2) {
            domainName = parts[parts.length - 1];
        }
        String finalDomainName = domainName;

        int proxyPort = Integer.parseInt(httpProxyPort);
        InetSocketAddress proxyAddress = new InetSocketAddress(domainName, proxyPort);

        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                log.info("Proxy select uri: {}", uri);
                log.info("Non-proxy hosts: {}", nonProxyHosts);

                List<String> nonProxyHostsList = Arrays.asList(
                        nonProxyHosts.replace("\"", "").split("\\|")
                );
                log.info("Non-proxy hosts list: {}", nonProxyHostsList);

                if (nonProxyHostsList.contains(uri.getHost())) {
                    log.info("Proxy is NOT used for this call");
                    return List.of(Proxy.NO_PROXY);
                } else {
                    log.info("Creating proxy server='{}' domainName = '{}' port='{}' -> {}",
                            httpProxyServer, finalDomainName, httpProxyPort, proxyAddress);
                    return List.of(new Proxy(Proxy.Type.HTTP, proxyAddress));
                }
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                log.error("Failed to connect to {} at {}. Reason: {}", uri, sa, ioe.getMessage());
            }
        };
    }

    public UniteUserDto validateBiometricToken(BiometricTokenValidationDto biometricTokenDto)
            throws BiometricTokenValidationFailedException {

        try {
            String payload = objectMapper.writeValueAsString(biometricTokenDto);
            var response = this.postRequest(payload, VALIDATE_BIOMETRIC_TOKEN_ENDPOINT);

            if (response.statusCode() == 401) {
                log.error("Received a 401 response from UniteBFF: {}", response.body());
                throw new BiometricInvalidCredentialsException("Invalid biometric credentials");
            } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Received a non-200 response: {} body: {}",
                        response.statusCode(), response.body());
                throw new BiometricTokenValidationFailedException(
                        "Biometric token validation failed with status: " + response.statusCode());
            }

            UniteUserDto uniteUser = this.extractResponseDto(
                    response.body(), UniteUserDto.class);

            if (!uniteUser.getErrors().isEmpty()) {
                log.error("Error message from UniteBFF: {}", uniteUser.getErrors());
                throw new BiometricTokenValidationFailedException(
                        "Error message received from UniteBFF");
            }

            return uniteUser;

        } catch (BiometricTokenValidationFailedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BiometricTokenValidationFailedException(
                    "Biometric token validation failed: ", ex);
        }
    }

    private HttpResponse<String> postRequest(String payload, String endpoint) throws Exception {
        log.debug("Validate biometric token - Payload: {}", payload);

        URI endpointUri = UriComponentsBuilder.fromUri(uniteServiceUrl)
                .path(endpoint)
                .build()
                .toUri();

        String requestId = UUID.randomUUID().toString();

        HttpRequest request = HttpRequest.newBuilder(endpointUri)
                .header("x-request-id", requestId)
                .header("x-request-datetime", Instant.now().toString())
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        return httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public <T> T extractResponseDto(String jsonResponse,
                                    Class<T> responseDtoClassType) throws IOException {

        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        JsonNode embedded = rootNode.path("_embedded");
        JsonNode item = embedded.path("item");

        if (item.isMissingNode()) {
            throw new IOException(
                    "Path 'responseObject_embedded.item' not found in the MobileBFF response");
        }
        return objectMapper.treeToValue(item, responseDtoClassType);
    }
}
