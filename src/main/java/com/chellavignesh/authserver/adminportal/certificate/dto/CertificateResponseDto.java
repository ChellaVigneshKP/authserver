package com.chellavignesh.authserver.adminportal.certificate.dto;

import com.chellavignesh.authserver.adminportal.certificate.CertificateEntity;
import com.chellavignesh.authserver.adminportal.certificate.CertificateStatus;
import com.chellavignesh.authserver.enums.entity.CertificateType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
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
        return new CertificateResponseDto(
                a.getId(),
                a.getName(),
                a.getType(),
                a.getStatus(),
                StringUtils.defaultIfBlank(a.getValidTo(), ""),
                null,
                null,
                null,
                isExpired(a)
        );
    }

    private static boolean isExpired(CertificateEntity a) {
        if (a.getStatus() == CertificateStatus.EXPIRED) {
            return true;
        }
        if (a.getValidTo() != null) {
            Date date = null;
            try {
                DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
                date = df.parse(a.getValidTo());
            } catch (Exception _) {
            }
            return (new Date()).compareTo(date) > 0;
        }
        return true;
    }
}
