package com.chellavignesh.authserver.mfa.exception;

public class OtpGenerationFailedException extends Exception {
    public OtpGenerationFailedException(String message) {
        super(message);
    }

    public OtpGenerationFailedException(String message, Exception ex) {
        super(message, ex);
    }
}
