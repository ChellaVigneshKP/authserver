package com.chellavignesh.authserver.security;

import com.chellavignesh.authserver.cms.BrandUrlMappingService;
import com.chellavignesh.authserver.config.ApplicationConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MultiRouteAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String LOGIN_ENDPOINT_TEMPLATE = "%s/login";

    private final BrandUrlMappingService brandUrlMappingService;
    private final RedirectStrategy redirectStrategy;

    public MultiRouteAuthenticationEntryPoint(BrandUrlMappingService brandUrlMappingService, RedirectStrategy redirectStrategy) {

        this.brandUrlMappingService = brandUrlMappingService;
        this.redirectStrategy = redirectStrategy;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {

        redirectStrategy.sendRedirect(request, response, LOGIN_ENDPOINT_TEMPLATE.formatted(brandUrlMappingService.getUrlByBrand(getBrand(request))));
    }

    private String getBrand(HttpServletRequest request) {
        return request.getSession() == null ? null : (String) request.getSession().getAttribute(ApplicationConstants.BRANDING_INFO);
    }
}
