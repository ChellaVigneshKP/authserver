package com.chellavignesh.authserver.session;

import com.chellavignesh.authserver.adminportal.util.KPCVEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Base64;

public class KPCVPasswordEncoder implements PasswordEncoder {
    public static final Integer ENCODER_ID = 1;

    KPCVEncoder passwordHasher;

    public KPCVPasswordEncoder(HasherConfig hasherConfig) {
        this.passwordHasher = new KPCVEncoder(hasherConfig);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        String password = rawPassword != null ? new String(Base64.getDecoder().decode(rawPassword.toString())) : null;

        // Spring needs the encoded password to be prefixed with the ID of the encoder used
        return "{" + ENCODER_ID + "}" + passwordHasher.getHashPasswordHex(password);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        String password = rawPassword != null ? new String(Base64.getDecoder().decode(rawPassword.toString())) : null;
        return passwordHasher.validatePassword(password, encodedPassword);
    }
}
