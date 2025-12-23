package com.chellavignesh.authserver.config.token;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Base64;

@Component
public class TokenEndpointBodySignatureFilter extends OncePerRequestFilter {

    private final TokenEndpointResponseBodySigner tokenEndpointResponseBodySigner;

    public TokenEndpointBodySignatureFilter(TokenEndpointResponseBodySigner tokenEndpointResponseBodySigner) {
        this.tokenEndpointResponseBodySigner = tokenEndpointResponseBodySigner;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        filterChain.doFilter(request, responseWrapper);

        byte[] body = responseWrapper.getContentAsByteArray();
        String clientId = request.getParameter("client_id");

        byte[] signature = this.tokenEndpointResponseBodySigner.signResponseBody(body, clientId);

        responseWrapper.setHeader("x-signature", new String(Base64.getEncoder().encode(signature)));

        // re-add the body content to the response
        responseWrapper.copyBodyToResponse();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/oauth2/token");
    }
}
