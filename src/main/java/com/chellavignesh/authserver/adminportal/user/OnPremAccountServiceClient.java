package com.chellavignesh.authserver.adminportal.user;

import com.chellavignesh.authjavasdk.AccessTokenData;
import com.chellavignesh.authjavasdk.AuthClient;
import com.chellavignesh.authjavasdk.exceptions.FailedToGetAccessTokenException;
import com.chellavignesh.authjavasdk.exceptions.FailedToSignMessageException;
import com.chellavignesh.authjavasdk.exceptions.UnauthorizedException;
import com.chellavignesh.authserver.adminportal.externalsource.ExternalSourceService;
import com.chellavignesh.authserver.adminportal.externalsource.exception.InvalidBrandingException;
import com.chellavignesh.authserver.adminportal.metadata.dto.OutgoingMetadataDto;
import com.chellavignesh.authserver.adminportal.user.dto.*;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.chellavignesh.authserver.adminportal.user.exception.AccountSyncException;
import com.chellavignesh.authserver.session.AccountSearch;
import com.chellavignesh.libcrypto.dto.BaseRequestObject;
import com.chellavignesh.libcrypto.exception.BadRequestException;
import com.chellavignesh.libcrypto.service.impl.SchemaParserService;
import com.chellavignesh.libcrypto.service.util.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class OnPremAccountServiceClient {

    private static final Logger log = LoggerFactory.getLogger(OnPremAccountServiceClient.class);

    private final AuthClient authClient;
    private final URI onPremAccountServiceGetAccountsUrl;
    private final ExternalSourceService externalSourceService;

    public OnPremAccountServiceClient(AuthClient noProxyAuthClient, @Value("${ascensus.url.account}") URI onPremAccountServiceGetAccountsUrl, ExternalSourceService externalSourceService) {
        this.authClient = noProxyAuthClient;
        this.onPremAccountServiceGetAccountsUrl = onPremAccountServiceGetAccountsUrl;
        this.externalSourceService = externalSourceService;
    }

    /* -------------------------------------------------
       Get user account (profile search)
     ------------------------------------------------- */

    public Optional<CreateUserProfileDto> getUserAccount(String username, String brand, String schema) {
        try {
            var baseRequest = new BaseRequestObject();
            baseRequest.setSchema(schema);

            var payload = new AccountSearch(brand, "profile", new AccountSearch.Criteria(username, null, null, null));

            baseRequest.addProperty(baseRequest.getSchema(), payload);
            baseRequest.setPayload(payload);

            var response = sendRequest(baseRequest);

            if (response.statusCode() == 200) {
                var message = SchemaParserService.getJsonPayload(response.body());
                return SchemaParserService.parseMessage(message, CreateUserProfileDto.class);
            }
        } catch (BadRequestException e) {
            log.error("Failed to parse user search response from service", e);
        } catch (UnauthorizedException | IOException | InterruptedException e) {
            log.error("Failed to send user search request to account service", e);
        } catch (FailedToSignMessageException e) {
            log.error("Failed to sign search user request", e);
        } catch (FailedToGetAccessTokenException e) {
            log.error("Failed to get access token", e);
        } catch (Exception e) {
            log.error("Failed to get JSON payload from parser service or something else has gone wrong", e);
        }

        return Optional.empty();
    }

    /* -------------------------------------------------
       Username lookup
     ------------------------------------------------- */

    public Optional<CreateUserProfileDto> lookupUsername(String brand, String schema, Map<String, String> usernameLookupParams) {
        try {
            String email = usernameLookupParams.get("email");
            String last4SSN = usernameLookupParams.get("last4SSN");
            String accountNumber = usernameLookupParams.get("accountNumber");

            email = (email != null && email.isEmpty()) ? null : email;
            last4SSN = (last4SSN != null && last4SSN.isEmpty()) ? null : last4SSN;
            accountNumber = (accountNumber != null && accountNumber.isEmpty()) ? null : accountNumber;

            var baseRequest = new BaseRequestObject();
            baseRequest.setSchema(schema);

            var payload = new AccountSearch(brand, "username", new AccountSearch.Criteria(null, email, last4SSN, accountNumber));

            baseRequest.addProperty(baseRequest.getSchema(), payload);
            baseRequest.setPayload(payload);

            var response = sendRequest(baseRequest);

            if (response.statusCode() == 200) {
                var message = SchemaParserService.getJsonPayload(response.body());
                return SchemaParserService.parseMessage(message, CreateUserProfileDto.class);
            } else if (response.statusCode() == 409) {
                log.error("Username lookup resulted in more than one record");
                return Optional.of(CreateUserProfileDto.CONFLICT_USERNAME_LOOKUP);
            }
        } catch (BadRequestException e) {
            log.error("Failed to parse user search response from service", e);
        } catch (UnauthorizedException | IOException | InterruptedException e) {
            log.error("Failed to send user search request to account service", e);
        } catch (FailedToSignMessageException e) {
            log.error("Failed to sign search user request", e);
        } catch (FailedToGetAccessTokenException e) {
            log.error("Failed to get access token", e);
        } catch (Exception e) {
            log.error("Failed to get JSON payload from parser service or something else has gone wrong", e);
        }

        return Optional.empty();
    }

    /* -------------------------------------------------
       User approval status
     ------------------------------------------------- */

    public Optional<UserApprovalStatusDto> getUserApprovalStatus(String username, String brand, String schema) {
        try {
            var baseRequest = new BaseRequestObject();
            baseRequest.setSchema(schema);

            var payload = new AccountSearch(brand, "approvalStatus", new AccountSearch.Criteria(username, null, null, null));

            baseRequest.addProperty(baseRequest.getSchema(), payload);
            baseRequest.setPayload(payload);

            var response = sendRequest(baseRequest);

            if (response.statusCode() == 200) {
                var message = SchemaParserService.getJsonPayload(response.body());
                return SchemaParserService.parseMessage(message, UserApprovalStatusDto.class);
            } else {
                log.warn("Request on onPrem returned a non-200 response: {}", response.statusCode());
                log.warn("ResponseBody: {}", response.body());
            }
        } catch (BadRequestException e) {
            log.warn("Failed to parse user search response from service", e);
        } catch (UnauthorizedException | IOException | InterruptedException e) {
            log.warn("Failed to send user search request to account service", e);
        } catch (FailedToSignMessageException e) {
            log.warn("Failed to sign search user request", e);
        } catch (FailedToGetAccessTokenException e) {
            log.warn("Failed to get access token", e);
        } catch (Exception e) {
            log.warn("Failed to get JSON payload from parser service or something else has gone wrong", e);
        }

        return Optional.empty();
    }

    /* -------------------------------------------------
       Sync profile
     ------------------------------------------------- */

    public HttpResponse<String> syncProfile(ChangeProfileDto dto, OutgoingMetadataDto metadata, UserDetails userDetails, String branding, String requestId) throws AccountSyncException {

        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        try {
            log.info("Syncing profile for user: {}", userDetails.loginId());

            var externalSource = externalSourceService.findBySourceCode(branding).orElseThrow(() -> new InvalidBrandingException("External source record not found for brand: %s".formatted(branding)));

            String syncSchema = externalSource.getExternalType().syncSchema();

            if (StringUtils.isEmpty(syncSchema)) {
                throw new InvalidBrandingException("Invalid Sync schema: %s, branding: %s".formatted(syncSchema, branding));
            }

            UserProfileSyncPayload profileSyncPayload = this.createProfileSyncPayload(userDetails, dto, branding, metadata);

            log.info("Profile sync payload: {}", profileSyncPayload);

            var accessToken = authClient.getAccessToken();

            // Set timestamp after acquiring token as it may take time
            profileSyncPayload.setTimestamp(Instant.now().toString());

            ObjectMapper mapper = new ObjectMapper();

            String payload = "{" + "\"schema\":\"%s\",".formatted(syncSchema) + "\"%s\":%s".formatted(syncSchema, mapper.writeValueAsString(profileSyncPayload)) + "}";

            HttpResponse<String> response = putRequest(payload, accessToken, requestId);

            log.info("Profile sync response: {}", response.body());

            return response;
        } catch (Exception ex) {
            log.error("Failed to sync profile updates with downstream systems", ex);
            throw new AccountSyncException("Failed to sync profile updates with downstream systems", ex);
        }
    }

    private UserProfileSyncPayload createProfileSyncPayload(UserDetails userDetails, ChangeProfileDto dto, String branding, OutgoingMetadataDto metadata) {
        UserProfileSyncPayload.UserProfileSyncDto profileDto = new UserProfileSyncPayload.UserProfileSyncDto();

        profileDto.setMemberId(userDetails.memberId());
        profileDto.setFirstName(userDetails.firstName());
        profileDto.setLastName(userDetails.lastName());
        profileDto.setEmail(dto.getEmail());
        profileDto.setPhoneNumber(dto.getPhoneNumber());
        profileDto.setSecondaryPhoneNumber(dto.getSecondaryPhoneNumber());

        UserProfileSyncPayload syncPayload = new UserProfileSyncPayload();
        syncPayload.setBranding(branding);
        syncPayload.setOrgId(userDetails.profileOrgId().toString());
        syncPayload.setMetadata(Optional.ofNullable(metadata).orElseGet(OutgoingMetadataDto::createEmpty));
        syncPayload.setProfile(profileDto);

        return syncPayload;
    }

    public HttpResponse<String> syncPassword(String newPassword, OutgoingMetadataDto metadata, UserDetails userDetails, String branding, String requestId) throws AccountSyncException {

        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        try {
            var externalSource = externalSourceService.findBySourceCode(branding).orElseThrow(() -> new InvalidBrandingException("External source record not found for brand: %s".formatted(branding)));

            String syncSchema = externalSource.getExternalType().syncSchema();

            if (StringUtils.isEmpty(syncSchema)) {
                throw new InvalidBrandingException("Invalid Sync schema: %s, branding: %s".formatted(syncSchema, branding));
            }

            UserPasswordSyncPayload passwordSyncPayload = this.createPasswordSyncPayload(userDetails, newPassword, branding, metadata);

            var accessToken = authClient.getAccessToken();

            // Set timestamp after acquiring token as it take a bit of time to acquire token
            passwordSyncPayload.setTimestamp(Instant.now().toString());

            ObjectMapper mapper = new ObjectMapper();

            String payload = "{" + "\"schema\":\"%s\",".formatted(syncSchema) + "\"%s\":%s".formatted(syncSchema, mapper.writeValueAsString(passwordSyncPayload)) + "}";

            return putRequest(payload, accessToken, requestId);

        } catch (Exception ex) {
            throw new AccountSyncException("Failed to sync password changes with downstream systems", ex);
        }
    }

    private UserPasswordSyncPayload createPasswordSyncPayload(UserDetails userDetails, String newPassword, String branding, OutgoingMetadataDto metadata) {
        UserPasswordSyncPayload.UserPasswordSyncDto passwordDto = new UserPasswordSyncPayload.UserPasswordSyncDto();

        passwordDto.setLoginId(userDetails.loginId());
        passwordDto.setPassword(newPassword);

        UserPasswordSyncPayload.UserPasswordSyncProfileDto profile = new UserPasswordSyncPayload.UserPasswordSyncProfileDto();

        profile.setMemberId(userDetails.memberId());

        UserPasswordSyncPayload syncPayload = new UserPasswordSyncPayload();

        syncPayload.setBranding(branding);
        syncPayload.setOrgId(userDetails.profileOrgId().toString());
        syncPayload.setMetadata(Optional.ofNullable(metadata).orElseGet(OutgoingMetadataDto::createEmpty));
        syncPayload.setCredential(passwordDto);
        syncPayload.setProfile(profile);

        return syncPayload;
    }

    private HttpResponse<String> sendRequest(BaseRequestObject baseRequest) throws Exception {

        var bodyBytes = SchemaParserService.getJsonValue(baseRequest, baseRequest.getSchema()).getBytes(StandardCharsets.UTF_8);

        var request = HttpRequest.newBuilder(onPremAccountServiceGetAccountsUrl).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).timeout(Duration.ofSeconds(10)) // OAuth 2.0/OIDC standard: UserInfo endpoint (5–10s)
                .method("GET", HttpRequest.BodyPublishers.ofByteArray(bodyBytes)).build();

        return authClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), authClient.signRequestBody(bodyBytes));
    }

    private HttpResponse<String> putRequest(String payload, AccessTokenData accessToken, String requestId) throws IOException, InterruptedException, FailedToGetAccessTokenException, UnauthorizedException, FailedToSignMessageException {

        HttpRequest request = HttpRequest.newBuilder(onPremAccountServiceGetAccountsUrl).header(Constants.X_REQUEST_ID, requestId).header(Constants.X_REQUEST_DATETIME, Instant.now().toString()).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).timeout(Duration.ofSeconds(15)) // OAuth 2.0 standard: authorization endpoint (10–15s)
                .PUT(HttpRequest.BodyPublishers.ofString(payload)).build();

        return authClient.send(request, HttpResponse.BodyHandlers.ofString(), authClient.signRequestBody(payload.getBytes(), accessToken), accessToken);
    }
}
