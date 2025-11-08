package com.chellavignesh.authserver.adminportal.forgotusername.exception;

public class ConflictUsernameLookupSearchException extends Exception {
    public ConflictUsernameLookupSearchException(String message) {
        super(message);
    }

    public ConflictUsernameLookupSearchException(String message, Exception ex) {
        super(message, ex);
    }
}
