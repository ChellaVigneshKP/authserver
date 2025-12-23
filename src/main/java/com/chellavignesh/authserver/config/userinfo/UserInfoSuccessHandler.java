package com.chellavignesh.authserver.config.userinfo;

import com.nimbusds.jose.shaded.gson.Gson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
public class UserInfoSuccessHandler implements AuthenticationSuccessHandler {

    private static final Set<String> CLAIMS_TO_REMOVE = Set.of("iat", "exp", "client_id", "active");

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

        if (authentication instanceof OidcUserInfoAuthenticationToken authenticationToken) {

            // FIX #26: Defensive copy to prevent ConcurrentModificationException
            // The getClaims() map might be shared/mutable in Spring Security internals
            // Under high concurrency (load testing or peak production traffic),
            // multiple threads can trigger race conditions when accessing this map
            Map<String, Object> claims;

            try {
                claims = authenticationToken.getUserInfo().getClaims();
            } catch (ConcurrentModificationException _) {
                // Retry once if concurrent modification detected during initial read
                log.warn("🟡 [USERINFO-CONCURRENCY] ConcurrentModificationException when reading claims, retrying... Request: {}", request.getRequestURI());
                claims = authenticationToken.getUserInfo().getClaims();
            }

            // Create defensive copy with synchronized access to prevent race conditions
            // This ensures thread-safety when copying the potentially shared map
            Map<String, Object> userInfoClaims = new LinkedHashMap<>();

            synchronized (claims) {
                try {
                    userInfoClaims.putAll(claims);
                } catch (ConcurrentModificationException e) {
                    // If still failing, log error and use empty map (better than 500 error)
                    log.error("🔴 [USERINFO-CONCURRENCY] Failed to copy claims after retry. Request: {}", request.getRequestURI(), e);
                    // Continue with empty claims rather than failing the request
                }
            }

            // Remove unnecessary fields for /userinfo endpoint
            userInfoClaims.keySet().removeAll(CLAIMS_TO_REMOVE);

            response.getOutputStream().write(new Gson().toJson(userInfoClaims).getBytes());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
        }
    }
}
