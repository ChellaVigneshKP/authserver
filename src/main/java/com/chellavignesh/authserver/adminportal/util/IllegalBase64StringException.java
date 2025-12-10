package com.chellavignesh.authserver.adminportal.util;

public class IllegalBase64StringException extends IllegalArgumentException {
    public IllegalBase64StringException(String message) {
        super(message);
    }
}
