package com.chellavignesh.authserver.adminportal.credential.dto;

import com.chellavignesh.authserver.adminportal.credential.entity.Credential;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public record CredentialResponseDto(UUID id, String name, String value, String algorithm, String status,
                                    String expirationDate, boolean isExpired) {

    public static CredentialResponseDto fromCredential(Credential credential) {
        return new CredentialResponseDto(
                credential.getRowGuid(),
                credential.getName(),
                credential.getValue(),
                credential.getTokenAlgorithm().name(),
                String.valueOf(credential.getCredentialStatus() != null ? credential.getCredentialStatus().toString() : ""),
                credential.getExpireOn() != null ? credential.getExpireOn() : "",
                isExpired(credential)
        );
    }

    private static boolean isExpired(Credential credential) {
        if (credential.getExpireOn() != null) {
            Date date = null;
            try {
                DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
                date = df.parse(credential.getExpireOn());
            } catch (Exception _) {
            }
            return (new Date()).compareTo(date) > 0;
        }
        return true;
    }
}
