package com.chellavignesh.authserver.adminportal.certificate.exception;

public class FailedToStoreCertificateException extends RuntimeException {
    public FailedToStoreCertificateException(String message) {
        super(message);
    }

    public FailedToStoreCertificateException(String message, Throwable cause) {
        super(message, cause);
    }
}
