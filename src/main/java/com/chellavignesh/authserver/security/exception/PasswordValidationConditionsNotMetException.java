package com.chellavignesh.authserver.security.exception;

public class PasswordValidationConditionsNotMetException extends PasswordValidationException {
    public PasswordValidationConditionsNotMetException(String message) {
        super(message);
    }
}
