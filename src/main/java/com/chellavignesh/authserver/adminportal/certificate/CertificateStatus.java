package com.chellavignesh.authserver.adminportal.certificate;

public enum CertificateStatus {
    INACTIVE((byte) 0),
    ACTIVE((byte) 1),
    EXPIRED((byte) 2),
    SUSPENDED((byte) 3);

    private final byte value;

    private CertificateStatus(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    public static CertificateStatus fromByte(byte value) {
        for (CertificateStatus status : CertificateStatus.values()) {
            if (status.getValue() == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid value given for CertificateStatus" + value);
    }
}
