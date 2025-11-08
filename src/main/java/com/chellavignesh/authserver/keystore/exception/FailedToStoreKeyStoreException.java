package com.chellavignesh.authserver.keystore.exception;

public class FailedToStoreKeyStoreException extends Exception {
    public FailedToStoreKeyStoreException(String message) {
        super(message);
    }

    public FailedToStoreKeyStoreException(String message, Throwable ex) {
        super(message, ex);
    }
}
