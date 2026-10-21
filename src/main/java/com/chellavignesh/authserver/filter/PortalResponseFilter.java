package com.chellavignesh.authserver.filter;

import com.chellavignesh.authserver.adminportal.util.PortalResponses;
import com.chellavignesh.authserver.token.SignatureService;
import com.chellavignesh.authserver.token.exception.SignatureFailedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Base64;

/** Normalizes only the new portal surface; legacy management contracts remain scoped separately. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class PortalResponseFilter extends OncePerRequestFilter {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PortalResponseFilter.class);
    private final SignatureService signatures;

    public PortalResponseFilter(SignatureService signatures) { this.signatures = signatures; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) { return !PortalResponses.isPortalRequest(request); }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("x-request-id", PortalResponses.requestId(request));
        var buffered = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, buffered);
            if (buffered.getStatus() >= 400) {
                boolean wasSigned = buffered.containsHeader("x-signature");
                PortalResponses.writeProblem(request, buffered, buffered.getStatus());
                // A signature created inside the security chain must cover the normalized response bytes.
                if (wasSigned) {
                    try {
                        var signature = signatures.signBody(request.getHeader("Authorization"), buffered.getContentAsByteArray());
                        buffered.setHeader("x-signature", Base64.getEncoder().encodeToString(signature));
                    } catch (SignatureFailedException failure) {
                        buffered.setHeader("x-signature", "");
                        PortalResponses.writeProblem(request, buffered, 500);
                    }
                }
            }
            buffered.copyBodyToResponse();
        } catch (ServletException | IOException | RuntimeException failure) {
            // The outer availability boundary preserves typed dependency failures as retryable503.
            if (InfrastructureAvailabilityFilter.isDependencyUnavailable(failure)) throw failure;
            log.warn("Portal request failed before completion ({})", failure.getClass().getSimpleName());
            if (buffered.isCommitted()) return;
            buffered.setHeader("x-signature", "");
            PortalResponses.writeProblem(request, buffered, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            buffered.copyBodyToResponse();
        }
    }
}
