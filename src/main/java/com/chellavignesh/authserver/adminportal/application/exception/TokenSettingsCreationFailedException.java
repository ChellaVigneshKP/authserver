package com.chellavignesh.authserver.adminportal.application.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class TokenSettingsCreationFailedException extends Exception {
    public TokenSettingsCreationFailedException(String message) {
        super(message);
    }
}
