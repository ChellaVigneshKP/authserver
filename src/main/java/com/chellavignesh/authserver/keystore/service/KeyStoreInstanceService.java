package com.chellavignesh.authserver.keystore.service;

import com.chellavignesh.authserver.keystore.exception.FailedToCreateKeyStoreException;
import com.chellavignesh.authserver.keystore.exception.FailedToLoadKeyStoreException;
import com.chellavignesh.authserver.keystore.exception.FailedToStoreKeyStoreException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import static com.chellavignesh.authserver.keystore.KeyStoreConstants.KEY_STORE_TYPE;

@Service
public class KeyStoreInstanceService {
    public KeyStore getEmptyInstance() throws FailedToCreateKeyStoreException {
        try {
            var keyStore = KeyStore.getInstance(KEY_STORE_TYPE);
            keyStore.load(null, null);
            return keyStore;
        } catch (KeyStoreException e) {
            throw new FailedToCreateKeyStoreException("Failed to create empty key store instance", e);
        } catch (CertificateException | IOException | NoSuchAlgorithmException e) {
            throw new FailedToCreateKeyStoreException("Failed to load KeyStore", e);
        }
    }

    public KeyStore getInstance(byte[] bytes, String password) throws FailedToCreateKeyStoreException {
        try (var inputStream = new ByteArrayInputStream(bytes)) {
            var keyStore = KeyStore.getInstance(KEY_STORE_TYPE);
            keyStore.load(inputStream, password.toCharArray());
            return keyStore;
        } catch (KeyStoreException e) {
            throw new FailedToCreateKeyStoreException("Failed to create key store instance", e);
        } catch (CertificateException | IOException | NoSuchAlgorithmException e) {
            throw new FailedToCreateKeyStoreException("Failed to load KeyStore", e);
        }
    }

    public static byte[] store(KeyStore keyStore, String password) throws FailedToStoreKeyStoreException {
        try (var outputStream = new ByteArrayOutputStream()) {
            keyStore.store(outputStream, password.toCharArray());
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new FailedToStoreKeyStoreException("IO Error while saving", e);
        } catch (KeyStoreException e) {
            throw new FailedToStoreKeyStoreException("KeyStore was not initialized", e);
        } catch (NoSuchAlgorithmException e) {
            throw new FailedToStoreKeyStoreException("KeyStore algorithm not found", e);
        } catch (CertificateException e) {
            throw new FailedToStoreKeyStoreException("Certificate error", e);
        }
    }

    public static void load(KeyStore keyStore, byte[] bytes, String password) throws FailedToLoadKeyStoreException {
        try (var inputStream = new ByteArrayInputStream(bytes)) {
            keyStore.load(inputStream, password.toCharArray());
        } catch (IOException e) {
            throw new FailedToLoadKeyStoreException("IO Error while loading", e);
        } catch (NoSuchAlgorithmException e) {
            throw new FailedToLoadKeyStoreException("KeyStore algorithm not found", e);
        } catch (CertificateException e) {
            throw new FailedToLoadKeyStoreException("Certificate error", e);
        }
    }
}
