package com.chellavignesh.authserver.adminportal.credential.exception;

public class CredentialNotExpiredException extends RuntimeException {
    public CredentialNotExpiredException(String message) {
        super(message);
    }

    public CredentialNotExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
