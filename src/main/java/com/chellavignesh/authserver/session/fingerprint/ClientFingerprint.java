package com.chellavignesh.authserver.session.fingerprint;

import com.chellavignesh.authserver.session.exception.ClientFingerprintFailedException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;

public record ClientFingerprint(ZoneOffset zoneOffset, String acceptLanguage, String userAgent, String refererDomain) {

    // PERFORMANCE: ThreadLocal MessageDigest pool for thread-safe, efficient hashing
    private static final ThreadLocal<MessageDigest> SHA256_DIGEST_POOL =
            ThreadLocal.withInitial(() -> {
                try {
                    return MessageDigest.getInstance("SHA-256");
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException("SHA-256 algorithm not available", e);
                }
            });

    public byte[] getBytes() throws ClientFingerprintFailedException {
        var zoneOffset = this.zoneOffset == null ? "" : this.zoneOffset;
        var acceptLanguage = this.acceptLanguage == null ? "" : this.acceptLanguage;
        var userAgent = this.userAgent == null ? "" : this.userAgent;
        var refererDomain = this.refererDomain == null ? "" : this.refererDomain;

        var preImage = String.format("%s:%s:%s:%s", zoneOffset, acceptLanguage, userAgent, refererDomain).getBytes(StandardCharsets.UTF_8);

        try {
            var messageDigest = SHA256_DIGEST_POOL.get();
            messageDigest.reset(); // Ensure clean state
            return messageDigest.digest(preImage);
        } catch (Exception e) {
            throw new ClientFingerprintFailedException("Could not create client fingerprint", e);
        }
    }
}
