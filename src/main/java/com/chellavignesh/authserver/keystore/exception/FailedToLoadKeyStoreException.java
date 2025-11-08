package com.chellavignesh.authserver.keystore.exception;

public class FailedToLoadKeyStoreException extends Exception {
    public FailedToLoadKeyStoreException(String message) {
        super(message);
    }

    public FailedToLoadKeyStoreException(String message, Throwable ex) {
        super(message, ex);
    }
}
