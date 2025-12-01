package com.chellavignesh.authserver.adminportal.user;

public enum UserStatus {
    Inactive(0),
    Active(1);

    private Integer value;

    UserStatus(Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }

    public static UserStatus fromByte(byte value) {
        for (UserStatus status : UserStatus.values()) {
            if (status.getValue() == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid value given for UserStatus" + value);
    }
}
