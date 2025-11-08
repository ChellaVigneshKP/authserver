package com.chellavignesh.authserver.enums;

import com.chellavignesh.authserver.enums.entity.*;
import com.chellavignesh.authserver.enums.exception.EnumServiceLoadFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;

@Service
public class EnumService {
    private static final Logger log = LoggerFactory.getLogger(EnumService.class);

    public EnumService(EnumRepository enumRepository) throws EnumServiceLoadFailedException {
        enumRepository.loadEnumValues();
        checkEnumsLoaded();
        log.info("EnumService initialized and enum values loaded successfully.");
    }

    private void checkEnumsLoaded() throws EnumServiceLoadFailedException {
        var errors = new HashSet<String>();
        if (AuthFlowEnum.PKCE.getValue() == null) {
            errors.add("Enum IDs for AuthFlowEnum not found.");
        } else {
            log.info("Enum IDs for AuthFlowEnum loaded successfully.");
        }
        if (AlgorithmEnum.ES256.getValue() == null) {
            errors.add("Enum IDs for AlgorithmEnum not found.");
        } else {
            log.info("Enum IDs for AlgorithmEnum loaded successfully.");
        }
        if (ApplicationTypeEnum.WEB.getValue() == null) {
            errors.add("Enum IDs for ApplicationTypeEnum not found.");
        } else {
            log.info("Enum IDs for ApplicationTypeEnum loaded successfully.");
        }
        if (CertificateType.ORGANIZATION.getValue() == null) {
            errors.add("Enum IDs for CertificateType not found.");
        } else {
            log.info("Enum IDs for CertificateType loaded successfully.");
        }
        if (TokenTypeEnum.ACCESS_TOKEN.getValue() == null) {
            errors.add("Enum IDs for TokenTypeEnum not found.");
        } else {
            log.info("Enum IDs for TokenTypeEnum loaded successfully.");
        }
        if (AuthSessionStatusEnum.ACTIVE.getValue() == null) {
            errors.add("Enum IDs for AuthSessionStatusEnum not found.");
        } else {
            log.info("Enum IDs for AuthSessionStatusEnum loaded successfully.");
        }
        if (!errors.isEmpty()) {
            throw new EnumServiceLoadFailedException("EnumService failed to load with the following errors:\n- " + String.join("\n", errors));
        }
    }
}
