package com.chellavignesh.authserver.cms;

import com.chellavignesh.authserver.config.ApplicationConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Optional;

@Service
@EnableConfigurationProperties
public class BrandUrlMappingService {

    private final BrandUrlMap brandUrlMap;
    private final URI basePath;

    public BrandUrlMappingService(BrandUrlMap brandUrlMap, @Value("${server.base-path}") URI basePath) {

        this.basePath = basePath;
        this.brandUrlMap = brandUrlMap;
    }

    public URI getUrlByBrand(String brand) {
        return brand == null ? basePath : brandUrlMap.mappings().getOrDefault(brand, basePath);
    }

    public URI getDefaultUrl() {
        return basePath;
    }

    public boolean isDefault(String branding) {
        return branding == null || !brandUrlMap.mappings().containsKey(branding);
    }

    public String getBrandFromRequest(HttpServletRequest request) {
        var session = request.getSession(false);
        return session != null ? (String) session.getAttribute(ApplicationConstants.BRANDING_INFO) : null;
    }
}
