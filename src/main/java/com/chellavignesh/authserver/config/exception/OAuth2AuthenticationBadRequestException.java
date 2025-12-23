package com.chellavignesh.authserver.config.exception;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;


public class OAuth2AuthenticationBadRequestException extends OAuth2AuthenticationException {
    public OAuth2AuthenticationBadRequestException(String message) {
        super(new OAuth2Error("invalid_request", message, null));
    }
}
