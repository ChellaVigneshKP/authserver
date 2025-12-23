package com.chellavignesh.authserver.config.authorization;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2AuthorizationErrorResponseHandler implements AuthenticationFailureHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthorizationErrorResponseHandler.class);

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {

        logger.error("🚨 [OAUTH2-DEBUG] OAuth2 Authorization Authentication Failed!");
        logger.error(" - Request URI: {}", request.getRequestURI());
        logger.error(" - Query String: {}", request.getQueryString());
        logger.error(" - Exception Type: {}", exception.getClass().getSimpleName());
        logger.error(" - Exception Message: {}", exception.getMessage());

        if (exception instanceof OAuth2AuthorizationCodeRequestAuthenticationException oauth2Exception) {

            OAuth2Error error = oauth2Exception.getError();

            logger.error(" - OAuth2 Error Code: {}", error.getErrorCode());
            logger.error(" - OAuth2 Error Description: {}", error.getDescription());
            logger.error(" - OAuth2 Error URI: {}", error.getUri());

        } else if (exception instanceof OAuth2AuthenticationException oauth2Exception) {

            OAuth2Error error = oauth2Exception.getError();

            logger.error(" - OAuth2 Error Code: {}", error.getErrorCode());
            logger.error(" - OAuth2 Error Description: {}", error.getDescription());
            logger.error(" - OAuth2 Error URI: {}", error.getUri());
        }

        logger.error(" - Full Stack Trace:", exception);

        // Set response status and content
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");

        response.getWriter().write(String.format("{\"error\":\"%s\",\"error_description\":\"%s\"}", "invalid_request", exception.getMessage()));
    }
}

