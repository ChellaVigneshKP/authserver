package com.chellavignesh.authserver.session.exception;

public class FailedToProcessPasswordException extends Exception {
    public FailedToProcessPasswordException(String message) {
        super(message);
    }

    public FailedToProcessPasswordException(String message, Throwable cause) {
        super(message, cause);
    }
}
