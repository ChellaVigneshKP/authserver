package com.chellavignesh.authserver.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1) // Run first to capture total request time
@Slf4j
public class PerformanceLoggingFilter extends OncePerRequestFilter {

    private static final long SLOW_REQUEST_THRESHOLD_MS = 1000;      // 1 second
    private static final long VERY_SLOW_REQUEST_THRESHOLD_MS = 3000; // 3 seconds

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        String requestUri = request.getRequestURI();
        String method = request.getMethod();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();

            // Log slow requests with appropriate severity
            if (duration >= VERY_SLOW_REQUEST_THRESHOLD_MS) {
                log.error("🔴 [PERF-ALERT] VERY SLOW REQUEST: {} {} - {}ms (status: {})", method, requestUri, duration, status);
            } else if (duration >= SLOW_REQUEST_THRESHOLD_MS) {
                log.warn("🟡 [PERF-WARNING] Slow request: {} {} - {}ms (status: {})", method, requestUri, duration, status);
            } else {
                log.debug("🟢 [PERF] {} {} - {}ms (status: {})", method, requestUri, duration, status);
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip actuator/health endpoints to reduce noise
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health") || path.startsWith("/actuator/prometheus");
    }
}

