package com.chellavignesh.authserver.security.exception;

public class PasswordBlackListedException extends PasswordValidationException {
    public PasswordBlackListedException(String message) {
        super(message);
    }
}
