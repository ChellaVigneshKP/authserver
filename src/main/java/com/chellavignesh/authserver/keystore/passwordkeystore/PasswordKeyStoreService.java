package com.chellavignesh.authserver.keystore.passwordkeystore;

import com.chellavignesh.authserver.keystore.exception.FailedToCreateKeyStoreException;
import com.chellavignesh.authserver.keystore.exception.FailedToGenerateKeyException;
import com.chellavignesh.authserver.keystore.passwordkeystore.exception.FailedToCreatePasswordKeyStoreException;
import com.chellavignesh.authserver.keystore.service.KeyStoreInstanceService;
import com.chellavignesh.authserver.keystore.service.SecretKeyGenerator;
import com.chellavignesh.authserver.keystore.service.UuidGenerator;
import org.springframework.stereotype.Service;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.UUID;

@Service
public class PasswordKeyStoreService {
    private final UuidGenerator uuidGenerator;
    private final KeyStoreInstanceService keyStoreInstanceService;
    private final SecretKeyGenerator secretKeyGenerator;

    public PasswordKeyStoreService(UuidGenerator uuidGenerator, KeyStoreInstanceService keyStoreInstanceService, SecretKeyGenerator secretKeyGenerator) {
        this.uuidGenerator = uuidGenerator;
        this.keyStoreInstanceService = keyStoreInstanceService;
        this.secretKeyGenerator = secretKeyGenerator;
    }

    public PasswordKeyStore createPasswordKeyStore() throws FailedToCreatePasswordKeyStoreException {
        try {
            var passwordAlias = uuidGenerator.random();
            var wrapperKeyStore = keyStoreInstanceService.getEmptyInstance();
            KeyStore.ProtectionParameter protectionParameter = new KeyStore.PasswordProtection(passwordAlias.toString().toCharArray());
            wrapperKeyStore.setEntry(passwordAlias.toString(), new KeyStore.SecretKeyEntry(secretKeyGenerator.generateKey()), protectionParameter);
            return new PasswordKeyStore(wrapperKeyStore, passwordAlias);
        } catch (FailedToCreateKeyStoreException e) {
            throw new FailedToCreatePasswordKeyStoreException("Failed to get empty keystore instance", e);
        } catch (FailedToGenerateKeyException e) {
            throw new FailedToCreatePasswordKeyStoreException("Failed to generate secret key for password", e);
        } catch (KeyStoreException e) {
            throw new FailedToCreatePasswordKeyStoreException("Failed to set password entry in keystore", e);
        }
    }

    public PasswordKeyStore createPasswordKeyStore(UUID passwordAlias) throws FailedToCreatePasswordKeyStoreException {
        try {
            var wrapperKeyStore = keyStoreInstanceService.getEmptyInstance();
            return new PasswordKeyStore(wrapperKeyStore, passwordAlias);
        } catch (FailedToCreateKeyStoreException e) {
            throw new FailedToCreatePasswordKeyStoreException("Failed to get empty keystore instance", e);
        }
    }
}
