package com.chellavignesh.authserver.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/** A recovery PIN authorizes one password change in the same browser, SQL session and flow. */
@Service
public class RecoveryVerificationService {
    private final StringRedisTemplate redis;
    private final String namespace;

    public RecoveryVerificationService(StringRedisTemplate redis,
            @Value("${spring.session.redis.namespace:authserver:v2:session}") String namespace) {
        this.redis = redis;
        this.namespace = namespace;
    }

    public void verified(String browserSession, String authSession, String flow) {
        redis.opsForValue().set(key(browserSession, authSession, flow), "verified", Duration.ofMinutes(5));
    }

    public boolean isVerified(String browserSession, String authSession, String flow) {
        return "verified".equals(redis.opsForValue().get(key(browserSession, authSession, flow)));
    }

    public boolean consume(String browserSession, String authSession, String flow) {
        // GETDEL is atomic across requests and server instances; concurrent resets cannot reuse a PIN.
        return "verified".equals(redis.opsForValue().getAndDelete(key(browserSession, authSession, flow)));
    }

    private String key(String browserSession, String authSession, String flow) {
        if (browserSession == null || authSession == null || flow == null) {
            throw new IllegalArgumentException("Recovery session and flow are required");
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((browserSession + "|" + authSession + "|" + flow).getBytes(StandardCharsets.UTF_8));
            return namespace + ":recovery-verification:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public static class VerificationRequiredException extends RuntimeException {
        public VerificationRequiredException() {
            super("Complete verification before changing the password. Start verification again if it expired.");
        }
    }
}
