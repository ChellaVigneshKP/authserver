package com.chellavignesh.authserver.adminportal.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class UserCreationBadRequestException extends Exception {
    public UserCreationBadRequestException(String message) {
        super(message);
    }
}
