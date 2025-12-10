package com.chellavignesh.authserver.adminportal.user;

import lombok.Getter;

@Getter
public enum UserStatus {
    Inactive(0),
    Active(1);

    private final Integer value;

    UserStatus(Integer value) {
        this.value = value;
    }

    public static UserStatus fromInt(Integer value) {
        for (UserStatus status : UserStatus.values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid value given for UserStatus" + value);
    }
}
