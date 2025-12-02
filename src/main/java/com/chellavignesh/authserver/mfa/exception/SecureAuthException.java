package com.chellavignesh.authserver.mfa.exception;

public class SecureAuthException extends Exception {
    public SecureAuthException(String message) {
        super(message);
    }

    public SecureAuthException(String message, Exception ex) {
        super(message, ex);
    }
}
