package com.chellavignesh.authserver.adminportal.forgotusername.exception;

public class UserUpdatedLoginException extends RuntimeException {
    public UserUpdatedLoginException(String message) {
        super(message);
    }

    public UserUpdatedLoginException(String message, Exception ex) {
        super(message, ex);
    }
}
