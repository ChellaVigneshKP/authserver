package com.chellavignesh.authserver.adminportal.credential.exception;

public class CredentialExpiredException extends RuntimeException {
    public CredentialExpiredException(String message) {
        super(message);
    }

    public CredentialExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
