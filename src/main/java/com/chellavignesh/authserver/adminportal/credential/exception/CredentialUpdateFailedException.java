package com.chellavignesh.authserver.adminportal.credential.exception;

public class CredentialUpdateFailedException extends Exception {
    public CredentialUpdateFailedException(String message) {
        super(message);
    }

    public CredentialUpdateFailedException(String message, Throwable ex) {
        super(message, ex);
    }
}
