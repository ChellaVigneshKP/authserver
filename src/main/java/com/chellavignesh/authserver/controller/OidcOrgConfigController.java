package com.chellavignesh.authserver.controller;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.chellavignesh.authserver.cms.BrandUrlMappingService;
import com.chellavignesh.authserver.config.ApplicationConstants;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
public class OidcOrgConfigController {

    private final BrandUrlMappingService brandUrlMappingService;

    public OidcOrgConfigController(BrandUrlMappingService brandUrlMappingService) {
        this.brandUrlMappingService = brandUrlMappingService;
    }

    @GetMapping("/oauth2/.well-known/organizations/{orgId}/openid-configuration")
    public Map<String, Object> oidcConfig(HttpServletRequest request, @PathVariable UUID orgId, @SessionAttribute(name = ApplicationConstants.BRANDING_INFO, required = false) String brandingInfo) {
        String serverPath = brandUrlMappingService.getUrlByBrand(brandingInfo).toString();

        var config = new HashMap<String, Object>();

        config.put("issuer", "%s/oauth2".formatted(serverPath));
        config.put("authorization_endpoint", "%s/oauth2/oauth2/authorize".formatted(serverPath));
        config.put("token_endpoint", "%s/oauth2/token".formatted(serverPath));
        config.put("jwks_uri", "%s/oauth2/.well-known/organizations/%s/jwks.json".formatted(serverPath, orgId.toString()));
        config.put("userinfo_endpoint", "%s/userinfo".formatted(serverPath));
        config.put("end_session_endpoint", "%s/oauth2/connect/logout".formatted(serverPath));
        config.put("introspection_endpoint", "%s/oauth2/introspect".formatted(serverPath));

        return config;
    }
}

