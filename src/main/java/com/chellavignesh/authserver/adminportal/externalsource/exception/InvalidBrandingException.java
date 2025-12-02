package com.chellavignesh.authserver.adminportal.externalsource.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidBrandingException extends Exception {
    public InvalidBrandingException(String message) {
        super(message);
    }
}
