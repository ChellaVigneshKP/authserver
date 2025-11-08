package com.chellavignesh.authserver.adminportal.user.exception;

public class AccountSyncException extends Exception {
    public AccountSyncException(String message) {
        super(message);
    }

    public AccountSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
