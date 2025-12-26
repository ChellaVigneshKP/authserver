package com.chellavignesh.authserver.security;

import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.config.clientfingerprint.ClientFingerprintValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class RequestClientFingerprintFilter extends OncePerRequestFilter {

    private final ClientFingerprintValidator clientFingerprintValidator;

    public RequestClientFingerprintFilter(ClientFingerprintValidator clientFingerprintValidator) {
        this.clientFingerprintValidator = clientFingerprintValidator;
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof OAuth2IntrospectionAuthenticatedPrincipal principal && principal.hasClaim(ApplicationConstants.CLIENT_FINGERPRINT) && !clientFingerprintValidator.isValidSignature(request, principal.getClaim(ApplicationConstants.CLIENT_FINGERPRINT))) {

            log.trace("Fingerprints did not match");
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.getWriter().println("Fingerprints do not match");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/oauth2");
    }
}
