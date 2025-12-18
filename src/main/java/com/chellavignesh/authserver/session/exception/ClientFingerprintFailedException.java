package com.chellavignesh.authserver.session.exception;

public class ClientFingerprintFailedException extends RuntimeException {
    public ClientFingerprintFailedException(String message) {
        super(message);
    }

    public ClientFingerprintFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
