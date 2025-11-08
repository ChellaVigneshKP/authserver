package com.chellavignesh.authserver.adminportal.forgotusername.exception;

public class InvalidUserSessionException extends Exception {
    public InvalidUserSessionException(String message) {
        super(message);
    }

    public InvalidUserSessionException(String message, Exception ex) {
        super(message, ex);
    }
}
