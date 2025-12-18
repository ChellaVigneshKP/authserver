package com.chellavignesh.authserver.token.dto;

import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.crypto.SecretKey;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTokenDto {
    private TokenTypeEnum tokenType;
    private String subjectId;
    private UUID sessionId;
    private Integer applicationId;
    private String data;
    private boolean isOpaque;
    private SecretKey signingKey;
    private Integer timeToLive;
}
