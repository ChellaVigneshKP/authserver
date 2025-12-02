package com.chellavignesh.authserver.mfa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class FactorOption {
    private String value;
    private String text;
}
