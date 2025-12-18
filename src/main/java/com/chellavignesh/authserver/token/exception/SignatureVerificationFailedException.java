package com.chellavignesh.authserver.token.exception;

public class SignatureVerificationFailedException extends Exception {
    public SignatureVerificationFailedException(String message) {
        super(message);
    }

    public SignatureVerificationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
