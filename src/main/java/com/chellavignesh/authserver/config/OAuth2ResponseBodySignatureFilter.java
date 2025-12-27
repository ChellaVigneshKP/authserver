package com.chellavignesh.authserver.config;

import com.chellavignesh.authserver.token.SignatureService;
import com.chellavignesh.authserver.token.exception.SignatureFailedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Base64;

@Component
@Slf4j
public class OAuth2ResponseBodySignatureFilter extends OncePerRequestFilter {


    private final SignatureService signatureService;

    public OAuth2ResponseBodySignatureFilter(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        filterChain.doFilter(request, responseWrapper);

        byte[] body = responseWrapper.getContentAsByteArray();
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        byte[] signature;
        try {
            signature = this.signatureService.signBody(authorizationHeader, body);
        } catch (SignatureFailedException e) {
            log.warn("Could not sign body", e);
            signature = new byte[0];
        }

        responseWrapper.setHeader("x-signature", new String(Base64.getEncoder().encode(signature)));

        // Re-add the body content to the response
        responseWrapper.copyBodyToResponse();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/oauth2/introspect") && !path.startsWith("/userinfo");
    }
}

