package com.chellavignesh.authserver.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecretRotationResponse {
    private String clientId;
    private String newSecret;
    private String message;
}
