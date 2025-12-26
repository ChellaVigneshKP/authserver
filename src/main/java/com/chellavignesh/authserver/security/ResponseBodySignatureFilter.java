package com.chellavignesh.authserver.security;


import com.chellavignesh.authserver.token.SignatureService;
import com.chellavignesh.authserver.token.exception.SignatureFailedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Base64;

@Component
@Slf4j
public class ResponseBodySignatureFilter extends OncePerRequestFilter {


    private final SignatureService signatureService;

    public ResponseBodySignatureFilter(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain) throws ServletException, IOException {

        var responseWrapper = new ContentCachingResponseWrapper(response);

        filterChain.doFilter(request, responseWrapper);

        try {
            var authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            var body = responseWrapper.getContentAsByteArray();

            var signature = this.signatureService.signBody(authorizationHeader, body);

            responseWrapper.setHeader("x-signature", Base64.getEncoder().encodeToString(signature));
        } catch (SignatureFailedException e) {
            log.error("Could not sign response body", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        // re-add the body content to the response
        responseWrapper.copyBodyToResponse();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/api");
    }
}
