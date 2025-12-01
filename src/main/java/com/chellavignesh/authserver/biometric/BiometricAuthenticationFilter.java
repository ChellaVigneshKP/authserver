package com.chellavignesh.authserver.biometric;

import com.chellavignesh.authserver.config.ApplicationConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.io.IOException;

public class BiometricAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    private static final Logger logger = LoggerFactory.getLogger(BiometricAuthenticationFilter.class);

    private static final AntPathRequestMatcher DEFAULT_ANT_PATH_REQUEST_MATCHER = new AntPathRequestMatcher("/login-biometric", "POST");

    public BiometricAuthenticationFilter(AuthenticationManager authenticationManager) {
        super(DEFAULT_ANT_PATH_REQUEST_MATCHER);
        setAuthenticationManager(authenticationManager);
    }

    protected boolean requiresAuthentication(HttpServletRequest request, HttpServletResponse response) {
        if (!super.requiresAuthentication(request, response)) {
            return false;
        }
        String biometricToken = request.getParameter(ApplicationConstants.BIOMETRIC_TOKEN);
        String deviceId = request.getParameter(ApplicationConstants.BIOMETRIC_DEV_ID);
        return StringUtils.isNotEmpty(biometricToken) && StringUtils.isNotEmpty(deviceId);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        logger.debug("Attempt biometric authentication");
        request.getSession().removeAttribute(ApplicationConstants.BIOMETRIC_AUTH_ERROR);
        request.getSession().removeAttribute("error");
        try {
            String biometricToken = request.getParameter(ApplicationConstants.BIOMETRIC_TOKEN);
            String deviceId = request.getParameter(ApplicationConstants.BIOMETRIC_DEV_ID);
            if (StringUtils.isEmpty(biometricToken) || StringUtils.isEmpty(deviceId)) {
                throw new BiometricAuthenticationException("Biometric token or device id is empty");
            }
            var biometricAuthRequest = BiometricAuthenticationToken.unauthenticated(biometricToken, deviceId);
            return this.getAuthenticationManager().authenticate(biometricAuthRequest);
        } catch (Exception e) {
            logger.error("Failed to authenticate biometric token", e);
            throw e;
        }
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException, ServletException {
        logger.debug("Successful biometric authentication for user {}", authResult.getName());
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(authResult);
        request.getSession(true).setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
        super.successfulAuthentication(request, response, chain, authResult);
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException, ServletException {
        logger.debug("Failed biometric authentication for user {}", failed.getMessage());
        SecurityContextHolder.clearContext();
        request.getSession().setAttribute(ApplicationConstants.BIOMETRIC_AUTH_ERROR, true);
        if (failed instanceof BiometricAuthenticationException) {
            request.getSession().setAttribute("error", ApplicationConstants.BIOMETRIC_INVALID_CREDENTIALS);
        } else {
            request.getSession().setAttribute("error", ApplicationConstants.BIOMETRIC_GENERIC_ERROR);
        }
        super.unsuccessfulAuthentication(request, response, failed);
    }
}
