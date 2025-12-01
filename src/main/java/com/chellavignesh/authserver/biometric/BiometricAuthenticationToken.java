package com.chellavignesh.authserver.biometric;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
public class BiometricAuthenticationToken extends AbstractAuthenticationToken {
    private static final long serialVersionUID = 1620L;

    Object principal;
    String token;
    String deviceId;

    public BiometricAuthenticationToken() {
        super(null);
        this.setAuthenticated(false);
    }

    public BiometricAuthenticationToken(String token, String deviceId) {
        super(null);
        this.token = token;
        this.deviceId = deviceId;
        this.setAuthenticated(false);
    }

    public BiometricAuthenticationToken(Object principal, String token, Collection<? extends GrantedAuthority> mapAuthorities) {
        super(null);
        this.principal = principal;
        this.token = token;
        this.getAuthorities().addAll(mapAuthorities);
        this.setAuthenticated(true);
    }

    public static BiometricAuthenticationToken unauthenticated(String token, String deviceId) {
        return new BiometricAuthenticationToken(token, deviceId);
    }

    public static BiometricAuthenticationToken authenticated(Object principal, String credentials, Collection<? extends GrantedAuthority> mapAuthorities) {
        return new BiometricAuthenticationToken(principal, credentials, mapAuthorities);
    }

    @Override
    public Object getCredentials() {
        return this.token;
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }

    public String getDeviceId() {
        return this.deviceId;
    }
}
