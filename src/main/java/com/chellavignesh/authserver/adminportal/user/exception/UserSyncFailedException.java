package com.chellavignesh.authserver.adminportal.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class UserSyncFailedException extends Exception {
    public UserSyncFailedException(String message) {
        super(message);
    }
}
