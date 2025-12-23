package com.chellavignesh.authserver.config.exception;

public class RequestBodyDecodeFailureException extends Exception {
    public RequestBodyDecodeFailureException(String message) {
        super(message);
    }
}
