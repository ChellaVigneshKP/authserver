package com.chellavignesh.authserver.config.authorization;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class PreAuthorizationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {

                new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
            }

            request.getSession().invalidate();

        } catch (Exception ex) {
            log.error("Error invalidating Id session: {}", ex.getMessage(), ex);
        }

        SecurityContextHolder.clearContext();
        PreAuthorizationFilter.logoutUser(request, response);

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String requestURI = request.getRequestURI();
        if (!requestURI.endsWith("/oauth2/authorize")) {
            return true;
        }

        /*
         * The auth-server login UI will attempt an 'authorize' call after successful form submission.
         * When this happens the query params will include an empty string 'continue' parameter.
         * We can use this parameter to avoid immediately logging out the user.
         */
        var authContinue = request.getParameter("continue");
        return authContinue != null;
    }

    private static void logoutUser(HttpServletRequest request, HttpServletResponse response) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
    }
}

