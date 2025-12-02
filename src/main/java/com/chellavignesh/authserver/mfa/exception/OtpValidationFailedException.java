package com.chellavignesh.authserver.mfa.exception;

public class OtpValidationFailedException extends Exception {
    public OtpValidationFailedException(String message) {
        super(message);
    }

    public OtpValidationFailedException(String message, Exception ex) {
        super(message, ex);
    }
}
