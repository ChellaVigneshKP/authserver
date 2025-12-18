package com.chellavignesh.authserver.session.sso.exception;

public class UserUpdatedLoginException extends Exception {
    public UserUpdatedLoginException(String message) {
        super(message);
    }

    public UserUpdatedLoginException(String message, Exception ex) {
        super(message, ex);
    }
}
