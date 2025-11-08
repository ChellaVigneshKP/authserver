package com.chellavignesh.authserver.adminportal.application.exception;

public class ResourceAlreadyAssignedException extends RuntimeException {
    public ResourceAlreadyAssignedException(String message) {
        super(message);
    }
}
