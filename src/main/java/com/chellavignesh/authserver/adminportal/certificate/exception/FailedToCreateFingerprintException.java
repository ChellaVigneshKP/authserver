package com.chellavignesh.authserver.adminportal.certificate.exception;

public class FailedToCreateFingerprintException extends RuntimeException {
    public FailedToCreateFingerprintException(String message) {
        super(message);
    }

    public FailedToCreateFingerprintException(String message, Throwable cause) {
        super(message, cause);
    }
}
