package com.chellavignesh.authserver.adminportal.forgotcredentials.exceptions;

public class FailedToSendEmailException extends Exception {
    public FailedToSendEmailException(String message) {
        super(message);
    }

    public FailedToSendEmailException(String message, Exception ex) {
        super(message, ex);
    }
}
