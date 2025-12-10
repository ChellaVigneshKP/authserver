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
        // get the property value and print it out
        iterations = Integer.parseInt(hasherConfig.iterations());
        pepperAlias = hasherConfig.pepper();

        KeyStore ks = null;
        try {
            ks = KeyStore.getInstance(hasherConfig.keystoretype());
            ks.load(KPCVEncoder.class.getClassLoader().getResourceAsStream(hasherConfig.keystorefile()), hasherConfig.password().toCharArray()); // TODO pull these values from a secure vault
        } catch (NoSuchAlgorithmException | CertificateException | IOException | KeyStoreException e) {
            log.error("Error loading keystore", e);
        }

        KeyStore.SecretKeyEntry entry = null;

        try {
            entry = (KeyStore.SecretKeyEntry) ks.getEntry(pepperAlias, new KeyStore.PasswordProtection(hasherConfig.password().toCharArray()));
        } catch (NoSuchAlgorithmException | UnrecoverableEntryException | KeyStoreException e) {
            log.error("Error getting key entry", e);
        }

        SecretKey someKey = entry.getSecretKey();
        pepperKey = someKey.getEncoded();

        try {
            skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        } catch (NoSuchAlgorithmException e) {
            log.error("Algorithm PBKDF2WithHmacSHA512 not found", e);
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

            // make sure the pepper alias matches what the pepper alias passes in
            byte[] pepperAliasBytes = UUIDUtils.asBytes(UUID.fromString(pepperAlias));

            if (!Arrays.equals(pepperAliasHashBytes, pepperAliasBytes)) {
                throw new SecurityException("The hashed password is using a different keys.");
            }
            // generate new hash
            char[] passwordToHash = passwordToHash(plainPwd, pepperBytes);
            byte[] genHashBytes = generateHash(passwordToHash, saltBytes, iterations);
            validated = Arrays.equals(pwdHashBytes, genHashBytes);

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
        SecretKey sk = skf.generateSecret(spec);
        return sk.getEncoded();
    }

    public String getHashPasswordHex(String plainPassword) {
        return hf.formatHex(getHashPassword(plainPassword));
    }

    public byte[] getHashPassword(String plainPassword) {
        byte[] hashPassword = {0};

        try {
            hashPassword = hashPassword(plainPassword, iterations, pepperAlias, pepperKey);
        } catch (InvalidKeySpecException e) {
            log.error("Error while encoding password", e);
        }

        return hashPassword;
    }

    public boolean validatePassword(String plainPassword, String hashPassword) {
        return validatePassword(plainPassword, hashPassword, pepperAlias, pepperKey);
    }
}
