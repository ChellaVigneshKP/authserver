package com.chellavignesh.authserver.authcode.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;


@Data
@NoArgsConstructor
public class CreateAuthCodeDto {
    private Integer applicationId;
    private UUID sessionId;
    private String data;
}
