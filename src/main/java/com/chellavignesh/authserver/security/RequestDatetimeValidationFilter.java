package com.chellavignesh.authserver.security;

import com.chellavignesh.authserver.security.exception.RequestDatetimeMissingException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class RequestDatetimeValidationFilter extends OncePerRequestFilter {


    private final RequestDatetimeValidator requestDatetimeValidator;

    public RequestDatetimeValidationFilter(RequestDatetimeValidator requestDatetimeValidator) {
        this.requestDatetimeValidator = requestDatetimeValidator;
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain) throws ServletException, IOException {

        // For OAuth2 authorization requests, check query parameter first, then header
        String datetimeValue = null;

        if (request.getRequestURI().contains("/oauth2/authorize")) {
            // For OAuth2 authorization requests, the x-request-datetime is sent as a query parameter
            datetimeValue = request.getParameter("x-request-datetime");
            log.debug("OAuth2 authorization request - checking x-request-datetime query parameter: {}", datetimeValue);
        }

        // If not found in query parameter or not an OAuth2 request, check header
        if (datetimeValue == null) {
            datetimeValue = request.getHeader("x-request-datetime");
            log.debug("Checking x-request-datetime header: {}", datetimeValue);
        }

        var authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        try {
            var validated = this.requestDatetimeValidator.validateRequestDatetime(datetimeValue, authorizationHeader);

            if (validated) {
                filterChain.doFilter(request, response);
            } else {
                String message = "Request datetime is not within the application's configured transit time";
                log.error(message);
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                response.getWriter().println(message);
            }
        } catch (RequestDatetimeMissingException e) {
            String message = "Missing request datetime header or parameter";
            log.error(message, e);
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.getWriter().println(message);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/api") && !path.startsWith("/oauth2/introspect") && !path.startsWith("/userinfo");
    }
}

