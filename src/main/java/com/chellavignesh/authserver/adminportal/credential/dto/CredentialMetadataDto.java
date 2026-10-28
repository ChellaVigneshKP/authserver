package com.chellavignesh.authserver.adminportal.credential.dto;

import com.chellavignesh.authserver.adminportal.credential.entity.Credential;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import java.util.UUID;

public record CredentialMetadataDto(UUID id, String name, String type, String algorithm, String status,
                                    String expirationDate, boolean isExpired) {
    public static CredentialMetadataDto fromCredential(Credential credential, boolean expired) {
        return new CredentialMetadataDto(credential.getRowGuid(), credential.getName(),
                credential.getAuthFlow() == AuthFlowEnum.CLIENT_SECRET_JWT ? "SHARED_SECRET" : "PRIVATE_KEY",
                credential.getTokenAlgorithm().name(), credential.getCredentialStatus() != null ? credential.getCredentialStatus().toString() : "",
                credential.getExpireOn() != null ? credential.getExpireOn() : "", expired);
    }
}
