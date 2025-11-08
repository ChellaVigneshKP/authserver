package com.chellavignesh.authserver.enums.entity;

import lombok.Getter;

@Getter
public enum RangeTypeEnum {
    AUTH_CODE_TTL("AuthCodeTimeToLive"),
    ACCESS_TOKEN_TTL("AccessTokenTimeToLive"),
    DEVICE_CODE_TTL("DeviceCodeTimeToLive"),
    REFRESH_TOKEN_TTL("RefreshTokenTimeToLive"),
    MAX_REQUEST_TRANSIT_TIME("MaxRequestTransitTime");

    private final String rangeType;

    RangeTypeEnum(String rangeType) { this.rangeType = rangeType; }
}
