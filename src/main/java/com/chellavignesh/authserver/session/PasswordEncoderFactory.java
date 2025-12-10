package com.chellavignesh.authserver.session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordEncoderFactory {
    public static final Integer currentVersion = KPCVPasswordEncoder.ENCODER_ID;

    @Autowired
    private ApplicationContext context;

    public PasswordEncoder getCurrentEncoder() {
        return context.getBean(PasswordEncoder.class);
    }

    public String encode(String password) {
        return getCurrentEncoder().encode(password).substring(6);
    }

    public boolean matches(String inputPassword, String encodedPassword, Integer version) {
        return getCurrentEncoder().matches(inputPassword, "{" + version + "}" + encodedPassword);
    }
}
