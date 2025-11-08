package com.chellavignesh.authserver.adminportal.certificate;

import jakarta.annotation.PostConstruct;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.context.annotation.Configuration;

import java.security.Security;

@Configuration
public class BouncyCastleConfig {
    public static final String BOUNCY_CASTLE_PROVIDER = "BC";

    @PostConstruct
    public void configureSecurity() {
        Security.addProvider(new BouncyCastleProvider());
        Security.setProperty("crypto.policy", "unlimited");
    }

}
