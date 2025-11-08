package com.chellavignesh.authserver.adminportal.credential.secret.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class CreateSecretDto {
    private Integer orgId;
    private Integer appId;
    private String description;
    private byte[] mainKeyStoreBytes;
    private byte[] passwordKeyStoreBytes;
    private String passwordKeyId;
    private byte[] secretHashValue;
    private Date expireOn;
}
