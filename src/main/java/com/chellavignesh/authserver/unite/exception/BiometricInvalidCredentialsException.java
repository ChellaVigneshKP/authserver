package com.chellavignesh.authserver.unite.exception;

import org.springframework.security.core.AuthenticationException;

public class BiometricInvalidCredentialsException extends AuthenticationException {
    public BiometricInvalidCredentialsException(String message) {
        super(message);
    }
}
