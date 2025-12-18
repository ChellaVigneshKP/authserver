package com.chellavignesh.authserver.session;

import com.chellavignesh.authjavasdk.AuthClient;
import com.chellavignesh.authjavasdk.exceptions.FailedToGetAccessTokenException;
import com.chellavignesh.authjavasdk.exceptions.UnauthorizedException;
import com.chellavignesh.libcrypto.dto.BaseRequestObject;
import com.chellavignesh.libcrypto.exception.BadRequestException;
import com.chellavignesh.libcrypto.service.impl.SchemaParserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@Slf4j
public class NotificationEmailServiceClient {
    private final AuthClient authClient;
    private final URI notificationServiceUrl;

    public NotificationEmailServiceClient(AuthClient proxyAuthClient, @Value("${ascensus.url.notification}") URI notificationServiceUrl) {
        this.authClient = proxyAuthClient;
        this.notificationServiceUrl = notificationServiceUrl;
    }

    public boolean sendEmailNotification(NotificationEmail notificationEmail) {
        try {
            var baseRequest = new BaseRequestObject();
            baseRequest.addProperty("baseSchema", "urn:ascensus:notification");
            baseRequest.setSchema("urn:ascensus:notification:1.0:email");
            baseRequest.addProperty(baseRequest.getSchema(), notificationEmail);
            baseRequest.setPayload(notificationEmail);

            var response = sendRequest(baseRequest);

            if (response.statusCode() == 202) {
                return true;
            } else {
                log.warn("Request on notification service returned a non-200 response: {}", response.statusCode());
                log.warn("ResponseBody: {}", response.body());
            }
        } catch (BadRequestException e) {
            log.warn("Failed to parse notification email response from service", e);
        } catch (UnauthorizedException | IOException | InterruptedException e) {
            log.warn("Failed to send notification email request", e);
        } catch (FailedToGetAccessTokenException e) {
            log.warn("Failed to get access token", e);
        } catch (Exception e) {
            log.warn("Failed to get JSON payload from parser service or something else has gone wrong", e);
        }

        return false;
    }

    private HttpResponse<String> sendRequest(BaseRequestObject baseRequest) throws Exception {

        var bodyBytes = SchemaParserService.getJsonValue(baseRequest, baseRequest.getSchema()).getBytes(StandardCharsets.UTF_8);

        var request = HttpRequest.newBuilder(notificationServiceUrl).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).timeout(Duration.ofSeconds(10))
                // Enterprise auth standard: typical API timeout (10s)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes)).build();

        return authClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), authClient.signRequestBody(bodyBytes));
    }
}
