package com.chellavignesh.authserver.enums.entity;

import lombok.Getter;

@Getter
public enum GlobalConfigTypeEnum {
    MAX_PASSWORD_FAILURE_COUNT("MaxPasswordFailureCount"),
    DISALLOWED_RECENT_PASSWORD_COUNT("DisallowedRecentPasswordCount"),
    MAX_MFA_PIN_FAILURE_COUNT("MaxMfaPinFailureCount");

    private final String globalConfigType;

    GlobalConfigTypeEnum(String globalConfigType) {
        this.globalConfigType = globalConfigType;
    }
}
