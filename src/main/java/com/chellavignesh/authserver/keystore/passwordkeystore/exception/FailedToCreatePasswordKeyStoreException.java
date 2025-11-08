package com.chellavignesh.authserver.keystore.passwordkeystore.exception;

public class FailedToCreatePasswordKeyStoreException extends RuntimeException {
    public FailedToCreatePasswordKeyStoreException(String message) {
        super(message);
    }

    public FailedToCreatePasswordKeyStoreException(String message, Throwable ex) {
        super(message, ex);
    }
}
