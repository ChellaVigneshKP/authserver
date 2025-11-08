package com.chellavignesh.authserver.jose;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class KeyCryptoService {
    private final String ENCRYPTION_ALGORITHM = "AES";

    private static final ThreadLocal<MessageDigest> SHA256_DIGEST_POOL =
            ThreadLocal.withInitial(() -> {
                try {
                    return MessageDigest.getInstance("SHA-256");
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException("SHA-256 algorithm not available", e);
                }
            });

    @Value("${key-store.password}")
    private String masterKeyStorePassword;

    public static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public byte[] hashSecret(String secret) {
        MessageDigest digest = SHA256_DIGEST_POOL.get();
        digest.reset();
        return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
    }

    public SecretKey toSecretKey(byte[] secretBytes) {
        return new SecretKeySpec(secretBytes, ENCRYPTION_ALGORITHM);
    }

    public SecretKey encrypt(String input, String password) throws Exception {
        Key key = new SecretKeySpec(password.getBytes(), ENCRYPTION_ALGORITHM);
        Cipher c = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        c.init(Cipher.ENCRYPT_MODE, key);
        return new SecretKeySpec(c.doFinal(input.getBytes()), ENCRYPTION_ALGORITHM);
    }

    public String decrypt(byte[] encryptedData, String password) throws Exception {
        Key key = new SecretKeySpec(password.getBytes(), ENCRYPTION_ALGORITHM);
        Cipher c = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        c.init(Cipher.DECRYPT_MODE, key);
        return new String(c.doFinal(encryptedData));
    }
}
