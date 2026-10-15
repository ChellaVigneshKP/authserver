package com.chellavignesh.authserver.config;

import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Filters added to SecurityFilterChain must not also run as global servlet filters. */
@Configuration
public class SecurityFilterRegistrationConfig {
    private static <T extends Filter> FilterRegistrationBean<T> disabled(T filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    @Bean
    FilterRegistrationBean<com.chellavignesh.authserver.security.BrandingRequestBodyFilter> brandingRegistration(com.chellavignesh.authserver.security.BrandingRequestBodyFilter filter) {
        return disabled(filter);
    }
    @Bean
    FilterRegistrationBean<com.chellavignesh.authserver.security.RequestDatetimeValidationFilter> requestDatetimeRegistration(com.chellavignesh.authserver.security.RequestDatetimeValidationFilter filter) {
        return disabled(filter);
    }
    @Bean
    FilterRegistrationBean<com.chellavignesh.authserver.security.RequestClientFingerprintFilter> requestFingerprintRegistration(com.chellavignesh.authserver.security.RequestClientFingerprintFilter filter) {
        return disabled(filter);
    }
    @Bean
    FilterRegistrationBean<com.chellavignesh.authserver.security.RequestBodySignatureFilter> requestSignatureRegistration(com.chellavignesh.authserver.security.RequestBodySignatureFilter filter) {
        return disabled(filter);
    }
    @Bean
    FilterRegistrationBean<com.chellavignesh.authserver.security.ResponseBodySignatureFilter> responseSignatureRegistration(com.chellavignesh.authserver.security.ResponseBodySignatureFilter filter) {
        return disabled(filter);
    }
    @Bean
    FilterRegistrationBean<com.chellavignesh.authserver.config.authorization.PreAuthorizationFilter> preAuthorizationRegistration(com.chellavignesh.authserver.config.authorization.PreAuthorizationFilter filter) {
        return disabled(filter);
    }
    @Bean
    FilterRegistrationBean<com.chellavignesh.authserver.config.OAuth2ResponseBodySignatureFilter> oauthResponseSignatureRegistration(com.chellavignesh.authserver.config.OAuth2ResponseBodySignatureFilter filter) {
        return disabled(filter);
    }
    @Bean
    FilterRegistrationBean<com.chellavignesh.authserver.config.token.TokenEndpointBodySignatureFilter> tokenSignatureRegistration(com.chellavignesh.authserver.config.token.TokenEndpointBodySignatureFilter filter) {
        return disabled(filter);
    }
    @Bean
    FilterRegistrationBean<com.chellavignesh.authserver.filter.StaticResourceSessionFilter> staticSessionRegistration(com.chellavignesh.authserver.filter.StaticResourceSessionFilter filter) {
        return disabled(filter);
    }
    @Bean
    FilterRegistrationBean<com.chellavignesh.authserver.biometric.BiometricAuthenticationFilter> biometricRegistration(com.chellavignesh.authserver.biometric.BiometricAuthenticationFilter filter) {
        return disabled(filter);
    }
}
