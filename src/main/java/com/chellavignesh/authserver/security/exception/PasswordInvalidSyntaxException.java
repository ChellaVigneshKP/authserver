package com.chellavignesh.authserver.security.exception;

public class PasswordInvalidSyntaxException extends PasswordValidationException {
    public PasswordInvalidSyntaxException(String message) {
        super(message);
    }
}
