package com.chellavignesh.authserver.adminportal.credential.exception;

public class CredentialCreationFailedException extends Exception {
    public CredentialCreationFailedException(String message) {
        super(message);
    }

    public CredentialCreationFailedException(String message, Throwable ex) {
        super(message, ex);
    }
}
