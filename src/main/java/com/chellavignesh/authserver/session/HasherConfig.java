package com.chellavignesh.authserver.session;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "hasher")
public record HasherConfig (
        @NotBlank String iterations,
        @NotBlank String pepper,
        @NotBlank String keystoretype,
        @NotBlank String keystorefile,
        @NotBlank String password) {}
