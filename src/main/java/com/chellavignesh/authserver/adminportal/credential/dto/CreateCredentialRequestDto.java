package com.chellavignesh.authserver.adminportal.credential.dto;

import com.chellavignesh.authserver.adminportal.credential.CredentialStatus;
import com.chellavignesh.authserver.enums.entity.AlgorithmEnum;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateCredentialRequestDto {
    private Integer orgId;
    private Integer appId;
    private String name;
    private String description;
    private String type;
    private Integer secretId;
    private UUID certificateId;
    private Integer certId;
    private String algorithm;
    private AlgorithmEnum algorithmEnum;
    private AuthFlowEnum authFlow;
    private String fingerprint;
    private Date expireOn;
    private String status;
    private CredentialStatus credentialStatus;
}
