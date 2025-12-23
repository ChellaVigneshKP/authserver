package com.chellavignesh.authserver.config.introspect;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.config.RequestBodyDecoder;
import com.chellavignesh.authserver.config.exception.RequestBodyDecodeFailureException;
import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import com.chellavignesh.authserver.session.AuthSessionService;
import com.chellavignesh.authserver.token.TokenService;
import com.nimbusds.jose.shaded.gson.GsonBuilder;
import com.nimbusds.jose.shaded.gson.JsonPrimitive;
import com.nimbusds.jose.shaded.gson.JsonSerializer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class IntrospectionSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(IntrospectionSuccessHandler.class);

    static final String RESOURCE_ACCESS_CLAIM = "res_access";
    static final String RESOURCE_ACCESS_GRANTED = "GRANTED";
    static final String RESOURCE_ACCESS_DENIED = "DENIED";
    static final String SIGNING_KEY_CLAIM = "tsk";

    private final ApplicationService applicationService;
    private final TokenService tokenService;
    private final AuthSessionService authSessionService;
    private final boolean isFingerprintingEnabled;

    public IntrospectionSuccessHandler(ApplicationService applicationService, TokenService tokenService, AuthSessionService authSessionService, boolean isFingerprintingEnabled) {

        this.applicationService = applicationService;
        this.tokenService = tokenService;
        this.authSessionService = authSessionService;
        this.isFingerprintingEnabled = isFingerprintingEnabled;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

        var req = new ContentCachingRequestWrapper(request);
        req.getParameterMap();

        try {
            Map<String, String> data = RequestBodyDecoder.decode(req.getContentAsString());

            var auth = (OAuth2TokenIntrospectionAuthenticationToken) authentication;

            boolean resourceAccessAllowed = this.validateResourceAccess(auth, data);

            // Auth claims are immutable. We can only return new claims in the response,
            // but can't add them to the authorization.
            // We could do this in TokenIntrospector class where we can add claim to auth,
            // but we don't have access to the request body parameters in TokenIntrospector
            Map<String, Object> claims = new HashMap<>(auth.getTokenClaims().getClaims());

            String resourceAccessClaim = resourceAccessAllowed ? RESOURCE_ACCESS_GRANTED : RESOURCE_ACCESS_DENIED;

            claims.put(RESOURCE_ACCESS_CLAIM, resourceAccessClaim);

            // get the signing key for the access token and add to the claims
            var accessToken = this.tokenService.getByValue(data.get("token"), TokenTypeEnum.ACCESS_TOKEN);

            accessToken.ifPresent(token -> {
                byte[] signingKey = token.getSigningKey();
                if (signingKey != null) {
                    claims.put(SIGNING_KEY_CLAIM, Base64.getEncoder().encodeToString(signingKey));
                }
            });

            var gson = new GsonBuilder().registerTypeAdapter(
                    Instant.class, (JsonSerializer<Instant>) (instant, type, jsonSerializationContext) -> new JsonPrimitive(instant.toEpochMilli())).create();

            response.getOutputStream().write(gson.toJson(claims).getBytes());

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

        } catch (RequestBodyDecodeFailureException e) {
            throw new RuntimeException(e);
        }
    }

    boolean validateResourceAccess(OAuth2TokenIntrospectionAuthenticationToken authentication, Map<String, String> requestParameters) {

        // This arrives base64 encoded because there is a WAF rule that blocks all calls
        // including //localhost in the url or body
        log.trace("Validating resource access for request parameters: {}", requestParameters);

        String resUrl = decodeResource(requestParameters.get("res_url"));
        String resMethod = requestParameters.get("res_method");
        String resUrn = decodeResource(requestParameters.get("res_urn"));

        String clientId = authentication.getTokenClaims().getClaim(OAuth2TokenIntrospectionClaimNames.CLIENT_ID);

        if (Objects.isNull(clientId)) {
            log.warn("Access not granted to resource {} {} {}. ClientId missing in Authentication.", resMethod, resUrl, resUrn);
            return false;
        }

        var applicationResources = applicationService.getAllAssignedResourcesByClientId(clientId);

        boolean allowAccess = applicationResources.stream().anyMatch(resource -> resource.compareResource(resUrl, resMethod, resUrn));

        if (!allowAccess) {
            log.warn("Access not granted to client {} to resource {} {} {}.", clientId, resMethod, resUrl, resUrn);
        }

        return allowAccess;
    }

    private String decodeResource(String resource) {
        try {
            return new String(Base64.getDecoder().decode(java.net.URLDecoder.decode(resource, StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.error("Failed to base64 decode the resource: {}", e.toString());
            return resource;
        } catch (NullPointerException _) {
            return resource;
        }
    }
}
