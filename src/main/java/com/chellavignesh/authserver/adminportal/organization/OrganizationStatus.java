package com.chellavignesh.authserver.adminportal.organization;

import lombok.Getter;

@Getter
public enum OrganizationStatus {
    INACTIVE((byte) 0),
    ACTIVE((byte) 1);

    private byte value;

    private OrganizationStatus(byte value) {
        this.value = value;
    }

    public static OrganizationStatus fromByte(byte value) {
        for (OrganizationStatus status : OrganizationStatus.values()) {
            if (status.getValue() == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid value given for OrganizationStatus" + value);
    }
}
