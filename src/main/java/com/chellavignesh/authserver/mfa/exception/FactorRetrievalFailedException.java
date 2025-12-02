package com.chellavignesh.authserver.mfa.exception;

public class FactorRetrievalFailedException extends Exception {
    public FactorRetrievalFailedException(String message) {
        super(message);
    }

    public FactorRetrievalFailedException(String message, Exception ex) {
        super(message, ex);
    }
}
