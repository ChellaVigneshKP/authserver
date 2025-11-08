package com.chellavignesh.authserver.adminportal.forgotusername.exception;

public class InvalidUserSessionSecurityException extends Exception {
    public InvalidUserSessionSecurityException(String message) {
        super(message);
    }

    public InvalidUserSessionSecurityException(String message, Exception ex) {
        super(message, ex);
    }
}
