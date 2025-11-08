package com.chellavignesh.authserver.adminportal.credential.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SharedSecretCredentialDao {
    private Credential credential;
    private String secretValue;
}
