package com.chellavignesh.authserver.config.token;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CustomTokenErrorResponseHandler implements AuthenticationFailureHandler {

    private static final Logger logger = LoggerFactory.getLogger(CustomTokenErrorResponseHandler.class);

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {

        boolean isPkceContext = request.getParameter(PkceParameterNames.CODE_VERIFIER) != null;

        if (exception instanceof OAuth2AuthenticationException oae) {
            OAuth2Error error = oae.getError();

            boolean invalidGrant = OAuth2ErrorCodes.INVALID_GRANT.equals(error.getErrorCode());

            boolean descriptionHintsPkce = error.getDescription() != null && error.getDescription().toLowerCase().contains("pkce");

            if (invalidGrant && isPkceContext && descriptionHintsPkce) {
                String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
                String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);

                logger.warn("PKCE verification failed: client_id={}, grant_type={}, desc={}", clientId, grantType, error.getDescription());
            }
        }

        // Write a standards-compliant OAuth2 error response
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String errorCode = "invalid_request";
        String errorDescription = null;

        if (exception instanceof OAuth2AuthenticationException oae) {
            errorCode = oae.getError().getErrorCode();
            errorDescription = oae.getError().getDescription();
        }

        StringBuilder body = new StringBuilder();
        body.append('{').append("\"error\":\"").append(escape(errorCode)).append("\"");

        if (errorDescription != null) {
            body.append(',').append("\"error_description\":\"").append(escape(errorDescription)).append("\"");
        }

        body.append('}');
        response.getWriter().write(body.toString());
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
