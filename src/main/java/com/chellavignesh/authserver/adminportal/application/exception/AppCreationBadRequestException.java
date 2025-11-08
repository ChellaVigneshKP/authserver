package com.chellavignesh.authserver.adminportal.application.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AppCreationBadRequestException extends Exception {
    public AppCreationBadRequestException(String message) {
        super(message);
    }
}
