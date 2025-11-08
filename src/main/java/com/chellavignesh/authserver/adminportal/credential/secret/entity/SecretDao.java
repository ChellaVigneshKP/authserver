package com.chellavignesh.authserver.adminportal.credential.secret.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SecretDao {
    private Secret secret;
    private String secretValue;
}
