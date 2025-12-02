package com.chellavignesh.authserver.pkce.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
public class CreatePkceDto {
    private Integer applicationId;
    private UUID sessionId;
    private String data;
    private String algorithm;
    private String redirectUri;
}
