package com.chellavignesh.authserver.biometric;

import org.springframework.security.core.AuthenticationException;

public class BiometricAuthenticationException extends AuthenticationException {
    public BiometricAuthenticationException(String message) {
        super(message);
    }
}
