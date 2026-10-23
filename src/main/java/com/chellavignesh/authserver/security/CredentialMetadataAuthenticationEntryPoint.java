package com.chellavignesh.authserver.security;

import com.chellavignesh.authserver.adminportal.util.PortalResponses;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Pattern;

/** The new credential route gets portal errors; other management routes retain bearer defaults. */
@Component
public class CredentialMetadataAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Pattern PATH = Pattern.compile("/api/v2/organizations/[^/]+/applications/[^/]+/credentials/?");
    private final AuthenticationEntryPoint fallback = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        if (!PATH.matcher(request.getServletPath()).matches()) {
            fallback.commence(request, response, exception);
            return;
        }
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        PortalResponses.writeProblem(request, response, HttpServletResponse.SC_UNAUTHORIZED);
    }
}
