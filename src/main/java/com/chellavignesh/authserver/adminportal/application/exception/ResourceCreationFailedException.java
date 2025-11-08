package com.chellavignesh.authserver.adminportal.application.exception;

public class ResourceCreationFailedException extends RuntimeException {
    public ResourceCreationFailedException(String message) {
        super(message);
    }

    public ResourceCreationFailedException(String message, Exception ex) {
        super(message, ex);
    }
}
