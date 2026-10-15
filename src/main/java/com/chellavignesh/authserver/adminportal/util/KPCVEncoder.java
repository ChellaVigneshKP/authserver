package com.chellavignesh.authserver.adminportal.util;

import com.chellavignesh.authserver.session.HasherConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

public class KPCVEncoder {
    private static final Logger log = LoggerFactory.getLogger(KPCVEncoder.class);

    SecureRandom rnd = new SecureRandom();
    static HexFormat hf = HexFormat.of();
    SecretKeyFactory skf;

    static final int SALT_BYTES_LENGTH = 64;
    static final int PEPPER_KEY_BYTES_LENGTH = 16;
    static final int ITERATION_BYTES_LENGTH = 4;

    private final String pepperAlias;
    private final Integer iterations;
    private final byte[] pepperKey;

    public KPCVEncoder(HasherConfig hasherConfig) {
        iterations = Integer.parseInt(hasherConfig.iterations());
        if (iterations < 10000 || iterations > 2_000_000) {
            throw new IllegalArgumentException("Hasher iterations must be between 10000 and 2000000");
        }
        pepperAlias = UUID.fromString(hasherConfig.pepper()).toString();
        String location = hasherConfig.keystorefile();
        if (!location.contains(":")) {
            location = location.startsWith("/") ? "file:" + location : "classpath:" + location;
        }
        try (var input = new org.springframework.core.io.DefaultResourceLoader().getResource(location).getInputStream()) {
            KeyStore store = KeyStore.getInstance(hasherConfig.keystoretype());
            store.load(input, hasherConfig.password().toCharArray());
            var entry = store.getEntry(pepperAlias, new KeyStore.PasswordProtection(hasherConfig.password().toCharArray()));
            if (!(entry instanceof KeyStore.SecretKeyEntry secret)) {
                throw new IllegalStateException("Configured password pepper key is missing");
            }
            pepperKey = secret.getSecretKey().getEncoded();
            skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("Cannot load configured password hashing keystore", exception);
        }
    }

    public byte[] hashPassword(String plainPwd, int iterations, String pepperAlias, byte[] pepperBytes) throws InvalidKeySpecException {

        // prepare password to hash
        char[] passwordToHash = passwordToHash(plainPwd, pepperBytes);

        // generate hash for the prepared password
        byte[] saltBytes = new byte[SALT_BYTES_LENGTH];
        rnd.nextBytes(saltBytes);

        byte[] genHashBytes = generateHash(passwordToHash, saltBytes, iterations);

        // construct db pwd hash
        int pwdHashBytesLength = PEPPER_KEY_BYTES_LENGTH + SALT_BYTES_LENGTH + ITERATION_BYTES_LENGTH + genHashBytes.length;

        byte[] dbPwdHash = new byte[pwdHashBytesLength];
        ByteBuffer buffer = ByteBuffer.wrap(dbPwdHash);
        buffer.put(UUIDUtils.asBytes(UUID.fromString(pepperAlias)));
        buffer.put(saltBytes);
        buffer.put(ByteBuffer.allocate(ITERATION_BYTES_LENGTH).putInt(iterations).array());
        buffer.put(genHashBytes);
        dbPwdHash = buffer.array();
        return dbPwdHash;
    }

    public boolean validatePassword(String plainPwd, String pwdHash, String pepperAlias, byte[] pepperBytes) {
        boolean validated = false;

        try {
            // break down the incoming password hash
            byte[] dbPwdHashBytes = hf.parseHex(pwdHash);
            ByteBuffer buffer = ByteBuffer.wrap(dbPwdHashBytes).asReadOnlyBuffer();
            byte[] pepperAliasHashBytes = new byte[PEPPER_KEY_BYTES_LENGTH];
            buffer.get(pepperAliasHashBytes, 0, PEPPER_KEY_BYTES_LENGTH);
            byte[] saltBytes = new byte[SALT_BYTES_LENGTH];
            buffer.get(saltBytes, 0, SALT_BYTES_LENGTH);
            byte[] iterationsBytes = new byte[ITERATION_BYTES_LENGTH];
            buffer.get(iterationsBytes, 0, ITERATION_BYTES_LENGTH);
            byte[] pwdHashBytes = new byte[buffer.remaining()];
            buffer.get(pwdHashBytes, 0, buffer.remaining());
            // get iteration count
            int iterations = ByteBuffer.wrap(iterationsBytes).getInt();
            if (iterations < 10000 || iterations > 2_000_000 || pwdHashBytes.length != 64) return false;

            // make sure the pepper alias matches what the pepper alias passes in
            byte[] pepperAliasBytes = UUIDUtils.asBytes(UUID.fromString(pepperAlias));

            if (!Arrays.equals(pepperAliasHashBytes, pepperAliasBytes)) {
                throw new SecurityException("The hashed password is using a different keys.");
            }
            // generate new hash
            char[] passwordToHash = passwordToHash(plainPwd, pepperBytes);
            byte[] genHashBytes = generateHash(passwordToHash, saltBytes, iterations);
            validated = MessageDigest.isEqual(pwdHashBytes, genHashBytes);

        } catch (Exception ex) {
            log.error("Password validation error", ex);
        }

        return validated;
    }

    private char[] passwordToHash(String plainPwd, byte[] pepperBytes) {
        char[] password = plainPwd.toCharArray();
        char[] pepperKeyArray = hf.formatHex(pepperBytes).toCharArray();
        char[] passwordToHash = new char[pepperKeyArray.length + password.length];

        CharBuffer cb = CharBuffer.wrap(passwordToHash);
        cb.put(pepperKeyArray);
        cb.put(password);
        return cb.array();
    }

    private byte[] generateHash(char[] passwordToHash, byte[] saltBytes, int iterations) throws InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(passwordToHash, saltBytes, iterations, 512);
        try {
            synchronized (skf) {
                return skf.generateSecret(spec).getEncoded();
            }
        } finally {
            spec.clearPassword();
            Arrays.fill(passwordToHash, '\0');
        }
    }

    public String getHashPasswordHex(String plainPassword) {
        return hf.formatHex(getHashPassword(plainPassword));
    }

    public byte[] getHashPassword(String plainPassword) {
        byte[] hashPassword = {0};

        try {
            hashPassword = hashPassword(plainPassword, iterations, pepperAlias, pepperKey);
        } catch (InvalidKeySpecException e) {
            throw new IllegalStateException("Could not hash password", e);
        }

        return hashPassword;
    }

    public boolean validatePassword(String plainPassword, String hashPassword) {
        return validatePassword(plainPassword, hashPassword, pepperAlias, pepperKey);
    }
}
