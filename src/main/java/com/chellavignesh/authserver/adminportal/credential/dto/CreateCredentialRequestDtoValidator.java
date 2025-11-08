package com.chellavignesh.authserver.adminportal.credential.dto;

import com.chellavignesh.authserver.adminportal.credential.CredentialType;
import com.chellavignesh.authserver.enums.entity.AlgorithmEnum;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.util.Arrays;
import java.util.Date;

@Component
public class CreateCredentialRequestDtoValidator implements Validator {
    @Override
    public boolean supports(Class<?> clazz) {
        return CreateCredentialRequestDto.class.equals(clazz) || UpdateCredentialRequestDto.class.equals(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        if (target instanceof CreateCredentialRequestDto dto) {
            if (dto.getName() == null || dto.getName().isEmpty()) {
                errors.rejectValue("name", "Invalid", "Credential name must be provided");
            }
            if (dto.getType() == null) {
                errors.rejectValue("type", "Invalid", "Credential type must be provided");
            } else if (!Arrays.stream(CredentialType.values()).map(Enum::name).toList().contains(dto.getType().toUpperCase())) {
                errors.rejectValue("type", "Invalid", "Credential type must be one of " + Arrays.stream(CredentialType.values()).map(Enum::name).toList());
            } else if (CredentialType.PRIVATE_KEY.toString().equalsIgnoreCase(dto.getType().toUpperCase()) && dto.getCertificateId() == null) {
                errors.rejectValue("certificateId", "Invalid", "Certificate ID must be provided for private key credential");
            }
            if (dto.getAlgorithm() == null || dto.getAlgorithm().isEmpty()) {
                errors.rejectValue("algorithm", "Invalid", "Algorithm must be provided");
            } else if (!Arrays.stream(AlgorithmEnum.values()).map(Enum::name).toList().contains(dto.getAlgorithm().toUpperCase())) {
                errors.rejectValue("algorithm", "Invalid", "Algorithm must be one of " + Arrays.stream(AlgorithmEnum.values()).map(Enum::name).toList());
            }
            if (dto.getExpireOn() != null && dto.getExpireOn().before(new Date())) {
                errors.rejectValue("expireOn", "Invalid", "Expire on date must be in the future");
            }
        }
        if (target instanceof UpdateCredentialRequestDto dto) {
            if (dto.getStatus() == null || dto.getStatus().isEmpty()) {
                errors.rejectValue("status", "Invalid", "Status must be provided");
            }
            if (dto.getStatus() != null && !"Active".equals(dto.getStatus()) && !"Disabled".equals(dto.getStatus()) && !"Inactive".equals(dto.getStatus())) {
                errors.rejectValue("status", "Invalid", "Status must be one of Active, Disabled or Inactive");
            }
        }
    }

}
