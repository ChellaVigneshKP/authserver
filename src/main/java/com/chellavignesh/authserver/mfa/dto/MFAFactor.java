package com.chellavignesh.authserver.mfa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MFAFactor {
    private String type;
    private String id;
    private String value;
    private List<String> biometricTypes;
    private List<String> capabilities;
}
