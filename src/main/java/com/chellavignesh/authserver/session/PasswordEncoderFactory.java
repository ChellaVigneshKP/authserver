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
        String encoded = getCurrentEncoder().encode(password);
        String prefix = "{" + currentVersion + "}";
        if (!encoded.startsWith(prefix)) {
            throw new IllegalStateException("Password encoder returned an unexpected version");
        }
        return encoded.substring(prefix.length());
    }

    public boolean matches(String inputPassword, String encodedPassword, Integer version) {
        return getCurrentEncoder().matches(inputPassword, "{" + version + "}" + encodedPassword);
    }
}
