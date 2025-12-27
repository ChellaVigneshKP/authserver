package com.chellavignesh.authserver.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class OidcWellKnownOverwriteFilter extends OncePerRequestFilter {

    private static final String DEFAULT_OIDC_PROVIDER_CONFIGURATION_ENDPOINT_URI = "/.well-known/openid-configuration";

    private final RequestMatcher requestMatcher = new AntPathRequestMatcher(DEFAULT_OIDC_PROVIDER_CONFIGURATION_ENDPOINT_URI, HttpMethod.GET.name());

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!this.requestMatcher.matches(request)) {
            filterChain.doFilter(request, response);
        } else {
            try (ServletServerHttpResponse editableResponse = new ServletServerHttpResponse(response)) {

                editableResponse.setStatusCode(HttpStatus.NOT_FOUND);
                editableResponse.getBody().write(("Configuration is based on each organization.\n" + "Configuration for a specific organization can be found at " + "/services/oauth2/.well-known/organizations/<your Org Id>/openid-configuration").getBytes());
                editableResponse.flush();
            }
        }
    }
}
