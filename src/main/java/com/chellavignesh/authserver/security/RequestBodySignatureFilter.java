package com.chellavignesh.authserver.security;

import com.chellavignesh.authserver.token.SignatureService;
import com.chellavignesh.authserver.token.exception.SignatureVerificationFailedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestBodySignatureFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestBodySignatureFilter.class);

    private final SignatureService signatureService;

    public RequestBodySignatureFilter(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain) throws ServletException, IOException {

        var requestWrapper = new CachedBodyHttpServletRequest(request);

        var signatureHeader = requestWrapper.getHeader("x-signature");
        if (!StringUtils.hasText(signatureHeader)) {
            if (signatureService.isSignatureRequired()) {
                logger.error("Missing request body signature");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getOutputStream().println("Missing request body signature");
                return;
            } else {
                logger.warn("Missing request body signature");
            }
        } else {
            try {
                var authorizationHeader = requestWrapper.getHeader(HttpHeaders.AUTHORIZATION);
                var body = requestWrapper.getInputStream().readAllBytes();

                var verified = this.signatureService.verifySignature(authorizationHeader, signatureHeader, body);

                if (!verified) {
                    logger.error("Invalid request body signature: {}", signatureHeader);
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getOutputStream().println("Invalid request body signature");
                    return;
                }
            } catch (SignatureVerificationFailedException e) {
                logger.error("could not verify request body signature", e);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }
        }

        filterChain.doFilter(requestWrapper, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/api") || matchesPostCertificateEndpoint(path, request.getMethod());
    }

    private boolean matchesPostCertificateEndpoint(String path, String method) {

        return path.matches("/api/v1/organizations/[^/]+/certificates") && HttpMethod.POST.matches(method);
    }
}

