package com.chellavignesh.authserver.session.sso.exception;

public class InactiveAuthSessionException extends Exception {
    public InactiveAuthSessionException(String message) {
        super(message);
    }
}
