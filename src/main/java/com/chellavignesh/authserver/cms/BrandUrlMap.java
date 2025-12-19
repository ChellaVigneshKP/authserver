package com.chellavignesh.authserver.cms;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Map;

@ConfigurationProperties(prefix = "brand")
public record BrandUrlMap(Map<String, URI> mappings) {
}
