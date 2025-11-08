package com.chellavignesh.authserver.keystore.exception;

public class FailedToCreateKeyStoreException extends Exception {
    public FailedToCreateKeyStoreException(String message) {
        super(message);
    }

    public FailedToCreateKeyStoreException(String message, Throwable ex) {
        super(message, ex);
    }
}
