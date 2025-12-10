package com.chellavignesh.authserver.security.exception;

public class PasswordIncorrectSemanticException extends PasswordValidationException {
    public PasswordIncorrectSemanticException(String message) {
        super(message);
    }
}
