package com.chellavignesh.authserver.keystore.parser;

import com.chellavignesh.authserver.adminportal.certificate.PemParseResults;
import com.chellavignesh.authserver.adminportal.certificate.PemParser;
import com.chellavignesh.authserver.adminportal.certificate.exception.InvalidFileException;
import com.chellavignesh.authserver.adminportal.certificate.exception.InvalidPemException;
import com.chellavignesh.authserver.keystore.KeyStorePair;
import com.chellavignesh.authserver.keystore.exception.*;
import com.chellavignesh.authserver.keystore.passwordkeystore.PasswordKeyStoreService;
import com.chellavignesh.authserver.keystore.service.KeyStoreInstanceService;
import com.chellavignesh.authserver.keystore.service.SecretKeyGenerator;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.KeyStoreException;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

import static com.chellavignesh.authserver.keystore.KeyStoreConstants.ALIAS;

@Service
public class PemKeyStorePairParser implements KeyStorePairParser {
    private final PemParser pemParser;
    private final PasswordKeyStoreService passwordKeyStoreService;
    private final KeyStoreInstanceService keyStoreInstanceService;
    private final SecretKeyGenerator secretKeyGenerator;

    public PemKeyStorePairParser(PemParser pemParser,
                                 PasswordKeyStoreService passwordKeyStoreService,
                                 KeyStoreInstanceService keyStoreInstanceService,
                                 SecretKeyGenerator secretKeyGenerator) {
        this.pemParser = pemParser;
        this.passwordKeyStoreService = passwordKeyStoreService;
        this.keyStoreInstanceService = keyStoreInstanceService;
        this.secretKeyGenerator = secretKeyGenerator;
    }

    public KeyStorePair parse(InputStream file, String password) throws FailedToCreateKeyStorePairException, InvalidPemException {
        return createKeyStorePair(pemParser.parse(file, password));
    }

    public KeyStorePair parse(byte[] mainKeyStoreBytes, byte[] passwordKeyStoreBytes, String password, String passwordAlias) throws FailedToCreateKeyStoreException, InvalidFileException {
        return createsKeyStorePair(mainKeyStoreBytes, passwordKeyStoreBytes, password, passwordAlias);
    }

    private KeyStorePair createKeyStorePair(PemParseResults results) throws FailedToCreateKeyStorePairException, InvalidPemException {
        try {
            var passwordKeyStore = passwordKeyStoreService.createPasswordKeyStore();
            var mainKeyStore = keyStoreInstanceService.getEmptyInstance();
            if (Objects.nonNull(results.privateKey())) {
                passwordKeyStore.addSecureElementPassword(secretKeyGenerator.generateKey());
                mainKeyStore.setKeyEntry(
                        ALIAS,
                        results.privateKey(),
                        passwordKeyStore.getSecureElementPassword().orElseThrow().toCharArray(),
                        results.certificateList().toArray(new Certificate[0])
                );
            } else {
                mainKeyStore.setCertificateEntry(ALIAS, results.certificateList().stream().findFirst().orElseThrow());
            }
            return new KeyStorePair(passwordKeyStore, mainKeyStore);
        } catch (NullPointerException e) {
            throw new FailedToCreateKeyStorePairException("Failed to perform operation on KeyStore", e);
        } catch (NoSuchElementException e) {
            throw new InvalidPemException("Failed to find KeyStorePassword in PEM file", e);
        } catch (IllegalArgumentException e) {
            throw new InvalidPemException(e.getMessage(), e);
        } catch (FailedToCreateKeyStoreException e) {
            throw new FailedToCreateKeyStorePairException("Failed to create empty KeyStore instance", e);
        } catch (FailedToGenerateKeyException e) {
            throw new FailedToCreateKeyStorePairException("Failed to generate secret key for password", e);
        } catch (UnrecoverableKeyException e) {
            throw new FailedToCreateKeyStorePairException("Failed to recover KeyStorePassword", e);
        } catch (KeyStoreException e) {
            throw new FailedToCreateKeyStorePairException("Failed to create KeyStorePair from PEM file", e);
        } catch (KeyAlreadyExistsException e) {
            throw new FailedToCreateKeyStorePairException("Alias already exists in KeyStore", e);
        }
    }

    private KeyStorePair createsKeyStorePair(byte[] mainKeyStoreBytes, byte[] passwordKeyStoreBytes, String password, String passwordAlias) throws FailedToCreateKeyStoreException, InvalidPemException {
        try {
            var passwordKeyStore = passwordKeyStoreService.createPasswordKeyStore(UUID.fromString(passwordAlias));
            passwordKeyStore.load(passwordKeyStoreBytes, password);
            var mainKeyStore = keyStoreInstanceService.getInstance(mainKeyStoreBytes, passwordKeyStore.getKeyStorePassword().orElseThrow());
            return new KeyStorePair(passwordKeyStore, mainKeyStore);
        } catch (NullPointerException e) {
            throw new FailedToCreateKeyStoreException("Failed to perform operation on KeyStore", e);
        } catch (NoSuchElementException e) {
            throw new InvalidPemException("Failed to find KeyStorePassword in PEM file", e);
        } catch (FailedToLoadKeyStoreException e) {
            throw new FailedToCreateKeyStoreException("Failed to load KeyStore instance", e);
        } catch (UnrecoverableEntryException e) {
            throw new FailedToCreateKeyStoreException("Failed to recover KeyStorePassword", e);
        }
    }


}
