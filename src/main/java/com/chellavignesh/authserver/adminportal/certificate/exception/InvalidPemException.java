package com.chellavignesh.authserver.adminportal.certificate.exception;

public class InvalidPemException extends RuntimeException {
    public InvalidPemException(String message) {
        super(message);
    }

    public InvalidPemException(String message, Throwable cause) {
        super(message, cause);
    }
}

