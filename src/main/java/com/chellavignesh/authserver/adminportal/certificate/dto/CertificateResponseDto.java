package com.chellavignesh.authserver.adminportal.certificate.dto;

import com.chellavignesh.authserver.adminportal.certificate.CertificateEntity;
import com.chellavignesh.authserver.adminportal.certificate.CertificateStatus;
import com.chellavignesh.authserver.enums.entity.CertificateType;
import com.chellavignesh.authserver.adminportal.credential.CredentialExpiration;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.time.Clock;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Getter
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class CertificateResponseDto {
    private UUID id;
    private String name;
    private CertificateType type;
    private CertificateStatus status;
    private String validTO;
    private String fingerprint;
    private String thumbprint;
    private String certificate;
    @JsonProperty("isExpired")
    private boolean isExpired;

    public static CertificateResponseDto fromCertificateEntityReduce(CertificateEntity a) {
        return fromCertificateEntityReduce(a, Clock.systemUTC());
    }

    public static CertificateResponseDto fromCertificateEntityReduce(CertificateEntity a, Clock clock) {
        return new CertificateResponseDto(
                a.getId(),
                a.getName(),
                a.getType(),
                a.getStatus(),
                StringUtils.defaultIfBlank(a.getValidTo(), ""),
                null,
                null,
                null,
                isExpired(a, clock)
        );
    }

    public static CertificateResponseDto fromCertificateEntityExtended(CertificateEntity a) {
        return fromCertificateEntityExtended(a, Clock.systemUTC());
    }

    public static CertificateResponseDto fromCertificateEntityExtended(CertificateEntity a, Clock clock) {
        return new CertificateResponseDto(
                a.getId(),
                a.getName(),
                a.getType(),
                a.getStatus(),
                StringUtils.defaultIfBlank(a.getValidTo(), ""),
                StringUtils.defaultIfBlank(a.getFingerprint(), ""),
                StringUtils.defaultIfBlank(a.getThumbprint(), ""),
                StringUtils.defaultIfBlank(a.getCertificate(), ""),
                isExpired(a, clock)
        );
    }

    private static boolean isExpired(CertificateEntity certificate, Clock clock) {
        if (certificate.getStatus() == CertificateStatus.EXPIRED || certificate.getValidTo() == null) {
            return true;
        }
        try {
            // X509Certificate.checkValidity accepts the notAfter instant itself.
            return clock.millis() > CredentialExpiration.parse(certificate.getValidTo()).toEpochMilli();
        } catch (DateTimeParseException invalid) {
            return true;
        }
    }
}
