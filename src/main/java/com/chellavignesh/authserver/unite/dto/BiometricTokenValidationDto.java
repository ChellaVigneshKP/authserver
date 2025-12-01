package com.chellavignesh.authserver.unite.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class BiometricTokenValidationDto {
    private String planId;
    private String biometricToken;
    private String deviceUuid;
}
