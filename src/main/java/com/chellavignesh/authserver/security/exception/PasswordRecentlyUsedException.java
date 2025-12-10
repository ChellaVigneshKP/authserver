package com.chellavignesh.authserver.security.exception;

public class PasswordRecentlyUsedException extends PasswordValidationException {
    public PasswordRecentlyUsedException(String message) {
        super(message);
    }
}
