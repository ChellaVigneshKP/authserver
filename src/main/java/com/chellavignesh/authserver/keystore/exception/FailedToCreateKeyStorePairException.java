package com.chellavignesh.authserver.keystore.exception;

public class FailedToCreateKeyStorePairException extends Exception {
    public FailedToCreateKeyStorePairException(String message) {
        super(message);
    }

    public FailedToCreateKeyStorePairException(String message, Throwable ex) {
        super(message, ex);
    }
}
