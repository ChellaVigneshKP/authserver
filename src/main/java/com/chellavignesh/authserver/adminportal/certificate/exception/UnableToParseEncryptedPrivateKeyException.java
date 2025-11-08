package com.chellavignesh.authserver.adminportal.certificate.exception;

public class UnableToParseEncryptedPrivateKeyException extends RuntimeException {
    public UnableToParseEncryptedPrivateKeyException(String message) {
        super(message);
    }

    public UnableToParseEncryptedPrivateKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
