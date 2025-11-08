package com.chellavignesh.authserver.adminportal.application.exception;

public class ApplicationDataAccessException extends Exception {
    public ApplicationDataAccessException(String message, Exception ex) {
        super(message, ex);
    }
}
