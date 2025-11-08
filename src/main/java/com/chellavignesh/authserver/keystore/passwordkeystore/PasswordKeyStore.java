package com.chellavignesh.authserver.keystore.passwordkeystore;

import com.chellavignesh.authserver.keystore.exception.FailedToLoadKeyStoreException;
import com.chellavignesh.authserver.keystore.exception.FailedToStoreKeyStoreException;
import com.chellavignesh.authserver.keystore.exception.KeyAlreadyExistsException;
import com.chellavignesh.authserver.keystore.service.KeyStoreInstanceService;
import lombok.EqualsAndHashCode;

import javax.crypto.SecretKey;
import java.security.*;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static com.chellavignesh.authserver.keystore.KeyStoreConstants.ALIAS;

@EqualsAndHashCode
public class PasswordKeyStore {
    private final KeyStore keyStore;
    private final UUID keyStorePasswordAlias;

    public PasswordKeyStore(KeyStore keyStore, UUID keyStorePasswordAlias) {
        this.keyStore = keyStore;
        this.keyStorePasswordAlias = keyStorePasswordAlias;
    }

    public void addSecureElementPassword(SecretKey secureElementPasswordKey) throws KeyStoreException, KeyAlreadyExistsException, UnrecoverableKeyException {
        if (getSecureElementPassword().isPresent()) {
            throw new KeyAlreadyExistsException();
        }
        KeyStore.SecretKeyEntry secretKeyEntry = new KeyStore.SecretKeyEntry(secureElementPasswordKey);
        KeyStore.ProtectionParameter protectionParameter = new KeyStore.PasswordProtection(keyStorePasswordAlias.toString().toCharArray());
        keyStore.setEntry(ALIAS, secretKeyEntry, protectionParameter);
    }

    public String getKeyStorePasswordAlias() {
        return keyStorePasswordAlias.toString();
    }

    public byte[] store(String password) throws FailedToStoreKeyStoreException {
        return KeyStoreInstanceService.store(keyStore, password);
    }

    public void load(byte[] bytes, String password) throws FailedToLoadKeyStoreException {
        KeyStoreInstanceService.load(keyStore, bytes, password);
    }

    public Optional<String> getSecureElementPassword() throws UnrecoverableKeyException {
        return getPasswordFromKeyStore(ALIAS);
    }

    public Optional<String> getKeyStorePassword() throws UnrecoverableKeyException {
        return getPasswordFromKeyStore(keyStorePasswordAlias.toString());
    }

    private Optional<String> getPasswordFromKeyStore(String alias) throws UnrecoverableKeyException {
        try {
            var entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(alias, new KeyStore.PasswordProtection(keyStorePasswordAlias.toString().toCharArray()));
            return Optional.of(Base64.getEncoder().encodeToString(entry.getSecretKey().getEncoded()));
        } catch (KeyStoreException | UnrecoverableEntryException | NoSuchAlgorithmException _) {
            throw new UnrecoverableKeyException();
        } catch (NullPointerException e) {
            return Optional.empty();
        }
    }

}
