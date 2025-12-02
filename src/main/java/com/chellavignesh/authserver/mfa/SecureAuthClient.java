package com.chellavignesh.authserver.mfa;

import com.chellavignesh.authserver.mfa.exception.SecureAuthException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.protocol.HTTP;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SecureAuthClient {
    private final ClientDetailsService clientDetailsService;
    private final RestClient.Builder restClientBuilder;

    // Cache RestClient instances per baseUrl
    private final Map<String, RestClient> clientCache = new ConcurrentHashMap<>();

    @Autowired
    public SecureAuthClient(ClientDetailsService clientDetailsService,
                            @Qualifier("secureAuthRestClientBuilder") RestClient.Builder builder) {
        this.clientDetailsService = clientDetailsService;
        this.restClientBuilder = builder;
        log.info("SecureAuthClient initialized with timeout-configured RestClient.Builder");
    }

    public <T> T callSecureAuth(
            Integer realmId,
            String baseUrl,
            String realm,
            String endpoint,
            HttpMethod method,
            Object payload,
            Class<T> clazz) throws SecureAuthException {

        Map<String, String> clientDetails = clientDetailsService.getClientDetails(realmId);
        String clientId = clientDetails.get(ClientDetailsService.CLIENT_ID);
        String clientSecret = clientDetails.get(ClientDetailsService.CLIENT_SECRET);

        String dttm = getCurrentDateTimeGMT();
        String path = realm + endpoint;

        // Optimization: reuse RestClient for same baseUrl
        RestClient client = clientCache.computeIfAbsent(baseUrl,
                url -> restClientBuilder.baseUrl(url).build());

        RestClient.RequestBodySpec request = client.method(method)
                .uri(path)
                .header("Authorization", getSecureAuthAuthentication(dttm, path, method.toString(), payload, clientId, clientSecret))
                .header("X-SA-Ext-Date", dttm)
                .header(HTTP.CONTENT_TYPE, "application/json");

        if (payload != null) {
            request.body(payload);
        }

        RestClient.ResponseSpec response = request.retrieve();

        return response.body(clazz);
    }

    private String getCurrentDateTimeGMT() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss.SSS z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(calendar.getTime());
    }

    /**
     * Generates Authorization header for SecureAuth
     */
    private String getSecureAuthAuthentication(
            String dttm,
            String path,
            String method,
            Object requestDto,
            String clientId,
            String clientSecret) throws SecureAuthException {

        String payload = null;
        if (requestDto != null) {
            try {
                payload = new ObjectMapper().writeValueAsString(requestDto);
            } catch (JsonProcessingException e) {
                throw new SecureAuthException("Error parsing request data", e);
            }
        }

        // Step A: Build newline separated string
        StringBuilder sb = new StringBuilder();
        sb.append(method).append("\n");
        sb.append(dttm).append("\n");
        sb.append(clientId).append("\n");
        sb.append(path);
        if (payload != null) {
            sb.append("\n");
            sb.append(payload);
        }

        // Step B: Generate HMAC-SHA256 signature
        SecretKeySpec secretKeySpec = new SecretKeySpec(Hex.decode(clientSecret), "HmacSHA256");
        Mac mac;
        try {
            mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
        } catch (NoSuchAlgorithmException e) {
            throw new SecureAuthException("Failed to find HmacSHA256 algorithm", e);
        } catch (InvalidKeyException e) {
            throw new SecureAuthException("Invalid key while validating OTP", e);
        }

        // Step C: Encode signature
        String base64Secret = Base64.getEncoder().encodeToString(
                mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8))
        );

        String authEncodedValue = "%s:%s".formatted(clientId, base64Secret);
        String authValue = Base64.getEncoder().encodeToString(authEncodedValue.getBytes(StandardCharsets.UTF_8));

        return "Basic %s".formatted(authValue);
    }
}
