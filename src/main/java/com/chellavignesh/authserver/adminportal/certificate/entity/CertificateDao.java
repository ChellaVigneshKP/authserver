package com.chellavignesh.authserver.adminportal.certificate.entity;

public record CertificateDao(
        Integer id,
        Integer certificateTypeId,
        String orgUuid,
        String name,
        boolean isX509Cert,
        byte[] keyStoreBytes,
        int status,
        String fingerprint,
        String thumbprint,
        String validFrom,
        String validTo,
        byte[] passwordKeyStoreBytes,
        String passwordKeyId,
        String subject,
        String issuer
) {
}
