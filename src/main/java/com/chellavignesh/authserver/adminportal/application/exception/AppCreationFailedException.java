package com.chellavignesh.authserver.adminportal.application.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AppCreationFailedException extends Exception {
    public AppCreationFailedException(String message) {
        super(message);
    }

    public AppCreationFailedException(String message, Exception ex) {
        super(message, ex);
    }
}
