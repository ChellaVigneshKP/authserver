package com.chellavignesh.authserver.adminportal.credential.secret;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class SecretGenerator {
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SECRET_LENGTH = 40;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateSecret() {
        StringBuilder sb = new StringBuilder(SECRET_LENGTH);
        for (int i = 0; i < SECRET_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}
