package com.chellavignesh.authserver.adminportal.application.exception;

public class AppSettingsNotFoundException extends RuntimeException {
    public AppSettingsNotFoundException(String message) {
        super(message);
    }
}
