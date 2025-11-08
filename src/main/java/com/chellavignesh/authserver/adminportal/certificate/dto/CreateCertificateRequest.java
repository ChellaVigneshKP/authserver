package com.chellavignesh.authserver.adminportal.certificate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record CreateCertificateRequest(
        @NotEmpty(message = "Name must be provided") String name,
        String password,
        @NotEmpty(message = "Type must be provided") String type,
        @NotNull(message = "Certificate must be provided") MultipartFile certificate
) {
}
