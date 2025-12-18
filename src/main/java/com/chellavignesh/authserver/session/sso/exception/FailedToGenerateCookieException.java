package com.chellavignesh.authserver.session.sso.exception;

public class FailedToGenerateCookieException extends Exception {
    public FailedToGenerateCookieException(Exception e) {
        super(e);
    }
}
