package com.chellavignesh.authserver.session.exception;

public class MfaExpirySessionException extends Exception {
    public MfaExpirySessionException(String message) {
        super(message);
    }
}
