package com.chellavignesh.authserver.adminportal.application.exception;

public class TokenSettingsNotFoundException extends RuntimeException {
    private TokenSettingsNotFoundException() {
        super("Token Settings not found");
    }

    public TokenSettingsNotFoundException(String message) {
        super(message);
    }
}
