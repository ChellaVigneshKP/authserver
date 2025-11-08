package com.chellavignesh.authserver.adminportal.forgotusername.exception;

public class FailedToRecoverUsernameException extends Exception {
    public FailedToRecoverUsernameException(String message) {
        super(message);
    }

    public FailedToRecoverUsernameException(String message, Exception ex) {
        super(message, ex);
    }
}
