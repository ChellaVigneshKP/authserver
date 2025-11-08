package com.chellavignesh.authserver.adminportal.forgotusername.exception;

public class ChangeUserProfileBadRequestException extends Exception {
    public ChangeUserProfileBadRequestException(String message) {
        super(message);
    }

    public ChangeUserProfileBadRequestException(String message, Exception ex) {
        super(message, ex);
    }
}
