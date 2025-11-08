package com.chellavignesh.authserver.adminportal.forgotusername.exception;

public class InvalidUserSessionParametersException extends Exception {
    public InvalidUserSessionParametersException(String message) {
        super(message);
    }

    public InvalidUserSessionParametersException(String message, Exception ex) {
        super(message, ex);
    }
}
