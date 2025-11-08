package com.chellavignesh.authserver.keystore.service;

import com.chellavignesh.authserver.jose.KeyCryptoService;
import com.chellavignesh.authserver.keystore.exception.FailedToGenerateKeyException;
import org.springframework.stereotype.Service;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import java.security.InvalidParameterException;
import java.security.NoSuchAlgorithmException;

import static com.chellavignesh.authserver.keystore.KeyStoreConstants.AES_KEY_GENERATOR;

@Service
public class SecretKeyGenerator {
    public SecretKey generateKey() throws FailedToGenerateKeyException {
        try {
            var generator = KeyGenerator.getInstance(AES_KEY_GENERATOR);
            generator.init(128);
            return generator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new FailedToGenerateKeyException("No KeyGenerator instance available for specified algorithm", e);
        } catch (InvalidParameterException e) {
            throw new FailedToGenerateKeyException("Invalid key size", e);
        }
    }
}
