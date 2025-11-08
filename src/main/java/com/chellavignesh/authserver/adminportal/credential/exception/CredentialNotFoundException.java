package com.chellavignesh.authserver.adminportal.credential.exception;

public class CredentialNotFoundException extends Exception {
    public CredentialNotFoundException(String message) {
        super(message);
    }

    public CredentialNotFoundException(String message, Throwable ex) {
        super(message, ex);
    }
}
