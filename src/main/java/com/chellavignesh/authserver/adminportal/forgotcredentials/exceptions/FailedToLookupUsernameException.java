package com.chellavignesh.authserver.adminportal.forgotcredentials.exceptions;

public class FailedToLookupUsernameException extends Exception {
    public FailedToLookupUsernameException(String message) {
        super(message);
    }

    public FailedToLookupUsernameException(String message, Exception ex) {
        super(message, ex);
    }
}
