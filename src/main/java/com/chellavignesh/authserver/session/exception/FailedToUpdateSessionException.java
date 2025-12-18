package com.chellavignesh.authserver.session.exception;

public class FailedToUpdateSessionException extends Exception {
    public FailedToUpdateSessionException(String message) {
        super(message);
    }

    public FailedToUpdateSessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
