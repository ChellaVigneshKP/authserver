package com.chellavignesh.authserver.cms;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Map;

@ConfigurationProperties(prefix = "brand")
public record BrandUrlMap(Map<String, URI> mappings) {
    public BrandUrlMap {
        mappings = mappings == null ? Map.of() : Map.copyOf(mappings);
        mappings.forEach((brand, uri) -> {
            if (brand.isBlank() || uri.getHost() == null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getUserInfo() != null
                    || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                throw new IllegalArgumentException("Brand mappings must use absolute HTTP(S) base URLs without query or fragment");
            }
        });
    }
}
