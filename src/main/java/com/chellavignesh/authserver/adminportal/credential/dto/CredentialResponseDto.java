package com.chellavignesh.authserver.adminportal.credential.dto;

import com.chellavignesh.authserver.adminportal.credential.CredentialExpiration;
import com.chellavignesh.authserver.adminportal.credential.entity.Credential;
import java.time.Clock;
import java.util.UUID;

/** Legacy public contract: secret value deliberately removed. */
public record CredentialResponseDto(UUID id, String name, String algorithm, String status,
                                    String expirationDate, boolean isExpired) {
    public static CredentialResponseDto fromCredential(Credential credential) {
        return fromCredential(credential, CredentialExpiration.isExpired(credential, Clock.systemUTC()));
    }

    public static CredentialResponseDto fromCredential(Credential credential, boolean expired) {
        return new CredentialResponseDto(credential.getRowGuid(), credential.getName(), credential.getTokenAlgorithm().name(),
                credential.getCredentialStatus() != null ? credential.getCredentialStatus().toString() : "",
                credential.getExpireOn() != null ? credential.getExpireOn() : "", expired);
    }
}
