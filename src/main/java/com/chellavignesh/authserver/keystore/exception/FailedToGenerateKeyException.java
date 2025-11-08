package com.chellavignesh.authserver.keystore.exception;

public class FailedToGenerateKeyException extends Exception {
    public FailedToGenerateKeyException(String message) {
        super(message);
    }

    public FailedToGenerateKeyException(String message, Throwable ex) {
        super(message, ex);
    }
}
