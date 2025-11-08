package com.chellavignesh.authserver.keystore;

import com.chellavignesh.authserver.jose.KeyCryptoService;
import com.chellavignesh.authserver.keystore.entity.KeyStorePairDao;
import com.chellavignesh.authserver.keystore.exception.FailedToStoreKeyStoreException;
import com.chellavignesh.authserver.keystore.passwordkeystore.PasswordKeyStore;
import com.chellavignesh.authserver.keystore.service.KeyStoreInstanceService;

import javax.crypto.SecretKey;
import java.security.KeyStore;
import java.security.KeyStore.SecretKeyEntry;
import java.security.PrivateKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.X509Certificate;
import java.util.NoSuchElementException;
import java.util.Optional;

import static com.chellavignesh.authserver.keystore.KeyStoreConstants.ALIAS;

public class KeyStorePair {
    private final PasswordKeyStore passwordKeyStore;
    private final KeyStore mainKeyStore;

    public KeyStorePair(PasswordKeyStore passwordKeyStore, KeyStore mainKeyStore) {
        this.passwordKeyStore = passwordKeyStore;
        this.mainKeyStore = mainKeyStore;
    }

    public Optional<X509Certificate> getCertificate() {
        try {
            return Optional.ofNullable((X509Certificate) mainKeyStore.getCertificate(ALIAS));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<PrivateKey> getPrivateKey() {
        try {
            return Optional.ofNullable((PrivateKey) mainKeyStore.getKey(ALIAS, passwordKeyStore.getKeyStorePassword().orElseThrow().toCharArray()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<SecretKeyEntry> getSecretKeyEntry() {
        try {
            return Optional.ofNullable((SecretKeyEntry) mainKeyStore.getEntry(ALIAS, new KeyStore.PasswordProtection(passwordKeyStore.getKeyStorePassword().orElseThrow().toCharArray())));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<String> getSecret() {
        try {
            SecretKeyEntry secretKeyEntry = (SecretKeyEntry) mainKeyStore.getEntry(ALIAS, new KeyStore.PasswordProtection(passwordKeyStore.getKeyStorePassword().orElseThrow().toCharArray()));
            SecretKey secretKey = secretKeyEntry.getSecretKey();
            byte[] secretBytes = secretKey.getEncoded();
            KeyCryptoService keyCryptoService = new KeyCryptoService();
            return Optional.ofNullable(keyCryptoService.decrypt(secretBytes, passwordKeyStore.getKeyStorePassword().orElseThrow()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public KeyStorePairDao toDao(String password) throws FailedToStoreKeyStoreException {
        try {
            var mainKeyStoreBytes = KeyStoreInstanceService.store(mainKeyStore, passwordKeyStore.getKeyStorePassword().orElseThrow());
            return new KeyStorePairDao(passwordKeyStore.store(password), mainKeyStoreBytes, passwordKeyStore.getKeyStorePasswordAlias());
        } catch (NoSuchElementException e) {
            throw new FailedToStoreKeyStoreException("Could not find KeyStorePassword in PasswordKeyStore", e);
        } catch (UnrecoverableEntryException e) {
            throw new FailedToStoreKeyStoreException("Could not recover KeyStorePassword", e);
        }
    }

}
