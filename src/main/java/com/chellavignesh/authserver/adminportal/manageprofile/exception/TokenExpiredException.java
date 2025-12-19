package com.chellavignesh.authserver.adminportal.manageprofile.exception;

public class TokenExpiredException extends Exception {
    public TokenExpiredException(String message) {
        super(message);
    }
}
