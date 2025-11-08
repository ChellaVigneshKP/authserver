package com.chellavignesh.authserver.adminportal.credential.secret;

import com.chellavignesh.authserver.adminportal.application.exception.AppNotFoundException;
import com.chellavignesh.authserver.adminportal.credential.exception.TooManyCredentialsException;
import com.chellavignesh.authserver.adminportal.credential.secret.dto.CreateSecretDto;
import com.chellavignesh.authserver.adminportal.credential.secret.entity.Secret;
import com.chellavignesh.authserver.adminportal.credential.secret.entity.SecretDao;
import com.chellavignesh.authserver.adminportal.credential.secret.exception.SecretCreationBadRequestException;
import com.chellavignesh.authserver.adminportal.credential.secret.exception.SecretCreationFailedException;
import com.chellavignesh.authserver.jose.KeyCryptoService;
import com.chellavignesh.authserver.keystore.KeyStoreConfig;
import com.chellavignesh.authserver.keystore.KeyStorePair;
import com.chellavignesh.authserver.keystore.entity.KeyStorePairDao;
import com.chellavignesh.authserver.keystore.exception.FailedToCreateKeyStoreException;
import com.chellavignesh.authserver.keystore.exception.FailedToCreateKeyStorePairException;
import com.chellavignesh.authserver.keystore.exception.KeyAlreadyExistsException;
import com.chellavignesh.authserver.keystore.passwordkeystore.PasswordKeyStoreService;
import com.chellavignesh.authserver.keystore.service.KeyStoreInstanceService;
import com.chellavignesh.authserver.keystore.service.SecretKeyGenerator;
import org.springframework.stereotype.Service;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.UnrecoverableKeyException;
import java.util.Optional;

import static com.chellavignesh.authserver.keystore.KeyStoreConstants.ALIAS;

@Service
public class SecretService {
    private final SecretRepository secretRepository;
    private final PasswordKeyStoreService passwordKeyStoreService;
    private final KeyStoreInstanceService keyStoreInstanceService;
    private final SecretGenerator secretGenerator;
    private final SecretKeyGenerator keyGenerator;
    private final KeyCryptoService keyCryptoService;
    private final KeyStoreConfig keyStoreConfig;

    public SecretService(SecretRepository secretRepository, PasswordKeyStoreService passwordKeyStoreService, KeyStoreInstanceService keyStoreInstanceService, SecretGenerator secretGenerator, SecretKeyGenerator secretKeyGenerator, KeyCryptoService keyCryptoService, KeyStoreConfig keyStoreConfig) {
        this.secretRepository = secretRepository;
        this.passwordKeyStoreService = passwordKeyStoreService;
        this.keyStoreInstanceService = keyStoreInstanceService;
        this.secretGenerator = secretGenerator;
        this.keyGenerator = secretKeyGenerator;
        this.keyCryptoService = keyCryptoService;
        this.keyStoreConfig = keyStoreConfig;
    }

    public SecretDao createSecret(CreateSecretDto createSecretDto) throws SecretCreationFailedException, TooManyCredentialsException, SecretCreationBadRequestException, FailedToCreateKeyStorePairException, AppNotFoundException {
        String secret = secretGenerator.generateSecret();
        byte[] hashedSecret;
        KeyStorePairDao keyStorePairDao;
        try {
            hashedSecret = keyCryptoService.hashSecret(secret);
            var passwordKeyStore = passwordKeyStoreService.createPasswordKeyStore();
            var mainKeyStore = keyStoreInstanceService.getEmptyInstance();
            passwordKeyStore.addSecureElementPassword(keyGenerator.generateKey());
            mainKeyStore.setEntry(
                    ALIAS,
                    new KeyStore.SecretKeyEntry(
                            keyCryptoService.encrypt(secret, passwordKeyStore.getSecureElementPassword().orElseThrow())
                    ),
                    new KeyStore.PasswordProtection(passwordKeyStore.getKeyStorePassword().orElseThrow().toCharArray())
            );
            keyStorePairDao = new KeyStorePair(passwordKeyStore, mainKeyStore).toDao(keyStoreConfig.password());
        } catch (NullPointerException e) {
            throw new FailedToCreateKeyStorePairException("Failed to perform operation on KeyStore", e);
        } catch (FailedToCreateKeyStoreException e) {
            throw new FailedToCreateKeyStorePairException("Failed to create new keystore", e);
        } catch (UnrecoverableKeyException e) {
            throw new FailedToCreateKeyStorePairException("Failed to recover key from keystore", e);
        } catch (KeyStoreException e) {
            throw new FailedToCreateKeyStorePairException("Failed to set entry in keystore", e);
        } catch (KeyAlreadyExistsException e) {
            throw new FailedToCreateKeyStorePairException("Alias already exists in KeyStore", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        createSecretDto.setSecretHashValue(hashedSecret);
        createSecretDto.setMainKeyStoreBytes(keyStorePairDao.mainKeyStoreBytes());
        createSecretDto.setPasswordKeyStoreBytes(keyStorePairDao.passwordKeyStoreBytes());
        createSecretDto.setPasswordKeyId(keyStorePairDao.passwordAlias());
        return new SecretDao(secretRepository.create(createSecretDto), secret);
    }

    public Optional<Secret> getById(Integer secretId) {
        return secretRepository.getById(secretId);
    }

    public void deleteSecret(Integer secretId) {
        secretRepository.delete(secretId);
    }
}
