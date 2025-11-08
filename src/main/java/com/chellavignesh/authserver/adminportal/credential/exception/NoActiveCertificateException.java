package com.chellavignesh.authserver.adminportal.credential.exception;

public class NoActiveCertificateException extends RuntimeException {
    public NoActiveCertificateException(String message) {
        super(message);
    }

    public NoActiveCertificateException(String message, Throwable cause) {
        super(message, cause);
    }
}
