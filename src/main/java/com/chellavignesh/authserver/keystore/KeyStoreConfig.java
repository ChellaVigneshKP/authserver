package com.chellavignesh.authserver.keystore;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "key-store")
public record KeyStoreConfig(@NotBlank String password) {
}
