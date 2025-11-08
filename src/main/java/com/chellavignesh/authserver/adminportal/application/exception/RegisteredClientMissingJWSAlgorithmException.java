package com.chellavignesh.authserver.adminportal.application.exception;

public class RegisteredClientMissingJWSAlgorithmException extends RuntimeException {
    public RegisteredClientMissingJWSAlgorithmException(String message) {
        super(message);
    }
}
