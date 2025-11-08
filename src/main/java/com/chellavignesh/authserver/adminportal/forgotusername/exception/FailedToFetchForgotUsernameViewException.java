package com.chellavignesh.authserver.adminportal.forgotusername.exception;

public class FailedToFetchForgotUsernameViewException extends Exception {
    public FailedToFetchForgotUsernameViewException(String message) {
        super(message);
    }

    public FailedToFetchForgotUsernameViewException(String message, Exception ex) {
        super(message, ex);
    }
}
