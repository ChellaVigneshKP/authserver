package com.chellavignesh.authserver.adminportal.application.exception;

public class UsernameExistsException extends Exception {
    public UsernameExistsException(String message) {
        super(message);
    }
}
