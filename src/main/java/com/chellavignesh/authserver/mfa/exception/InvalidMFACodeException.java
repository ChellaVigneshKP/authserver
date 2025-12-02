package com.chellavignesh.authserver.mfa.exception;

public class InvalidMFACodeException extends Exception {
    public InvalidMFACodeException(String message) {
        super(message);
    }
}
