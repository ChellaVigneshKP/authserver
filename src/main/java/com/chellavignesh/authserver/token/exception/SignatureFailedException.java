package com.chellavignesh.authserver.token.exception;

public class SignatureFailedException extends Exception {
    public SignatureFailedException(String message) {
        super(message);
    }

    public SignatureFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
