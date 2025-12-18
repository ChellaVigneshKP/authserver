package com.chellavignesh.authserver.token;

import com.chellavignesh.authserver.keystore.exception.FailedToGenerateKeyException;
import org.springframework.stereotype.Service;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;

@Service
public class SigningKeyGenerator {

    public SecretKey generateKey() throws FailedToGenerateKeyException {
        try {
            var generator = KeyGenerator.getInstance("HmacSHA256");
            return generator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new FailedToGenerateKeyException("No KeyGenerator instance available for HmacSHA256 algorithm", e);
        }
    }
}
