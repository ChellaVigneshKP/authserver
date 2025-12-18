package com.chellavignesh.authserver.session.dto;

import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateAuthSessionDto {
    private Integer applicationId;
    private String subjectId;
    private String scope;
    private AuthFlowEnum authFlow;
    private byte[] clientFingerprint;
    private String clientId;
    private String branding;
}
