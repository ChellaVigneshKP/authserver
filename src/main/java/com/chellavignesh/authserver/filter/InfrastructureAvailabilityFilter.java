package com.chellavignesh.authserver.filter;

import com.chellavignesh.authserver.adminportal.util.PortalResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Session/security failures happen before MVC advice; keep dependency outages out of raw 500 pages. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InfrastructureAvailabilityFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(InfrastructureAvailabilityFilter.class);
    public static final String MESSAGE = "Service temporarily unavailable. Please try again shortly.";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException failure) {
            if (!isDependencyUnavailable(failure)) {
                throw failure;
            }
            log.warn("Infrastructure unavailable while serving {} {}", request.getMethod(), request.getRequestURI());
            if (response.isCommitted()) {
                // A response already sent cannot be replaced; avoid a second error dispatch.
                return;
            }
            response.reset();
            if (PortalResponses.isPortalRequest(request)) {
                PortalResponses.writeProblem(request, response, 503);
                return;
            }
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader("Retry-After", "5");
            response.setHeader("Cache-Control", "no-store");
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write(MESSAGE);
        }
    }

    public static boolean isDependencyUnavailable(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof DataAccessResourceFailureException || cause instanceof QueryTimeoutException
                    || cause instanceof io.lettuce.core.RedisConnectionException
                    || cause instanceof io.lettuce.core.RedisCommandTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
