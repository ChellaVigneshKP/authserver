package com.chellavignesh.authserver.unite.exception;

import org.springframework.security.core.AuthenticationException;

public class BiometricTokenValidationFailedException extends AuthenticationException {
    public BiometricTokenValidationFailedException(String message) {
        super(message);
    }

    public BiometricTokenValidationFailedException(String message, Throwable ex) {
        super(message, ex);
    }
}
