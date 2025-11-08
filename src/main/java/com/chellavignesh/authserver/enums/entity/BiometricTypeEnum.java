package com.chellavignesh.authserver.enums.entity;

import io.micrometer.common.util.StringUtils;

public enum BiometricTypeEnum {
    FINGERPRINT, FACE;

    public static BiometricTypeEnum fromString(String biometricType) {
        if (StringUtils.isNotBlank(biometricType)) {
            for (BiometricTypeEnum biometricTypeEnum : BiometricTypeEnum.values()) {
                if (biometricTypeEnum.name().equalsIgnoreCase(biometricType)) {
                    return biometricTypeEnum;
                }
            }
        }
        return null;
    }
}
