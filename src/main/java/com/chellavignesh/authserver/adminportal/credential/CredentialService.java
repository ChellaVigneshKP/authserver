package com.chellavignesh.authserver.adminportal.credential;

import com.chellavignesh.authserver.adminportal.application.ApplicationRepository;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.application.exception.AppNotFoundException;
import com.chellavignesh.authserver.adminportal.certificate.CertificateEntity;
import com.chellavignesh.authserver.adminportal.certificate.CertificateStatus;
import com.chellavignesh.authserver.adminportal.certificate.OrganizationCertificateService;
import com.chellavignesh.authserver.adminportal.credential.dto.CreateCredentialRequestDto;
import com.chellavignesh.authserver.adminportal.credential.dto.UpdateCredentialRequestDto;
import com.chellavignesh.authserver.adminportal.credential.entity.Credential;
import com.chellavignesh.authserver.adminportal.credential.entity.CredentialDao;
import com.chellavignesh.authserver.adminportal.credential.exception.*;
import com.chellavignesh.authserver.adminportal.credential.secret.SecretService;
import com.chellavignesh.authserver.adminportal.credential.secret.dto.CreateSecretDto;
import com.chellavignesh.authserver.adminportal.credential.secret.entity.Secret;
import com.chellavignesh.authserver.adminportal.credential.secret.entity.SecretDao;
import com.chellavignesh.authserver.adminportal.credential.secret.exception.SecretCreationBadRequestException;
import com.chellavignesh.authserver.adminportal.credential.secret.exception.SecretCreationFailedException;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import com.chellavignesh.authserver.keystore.exception.FailedToCreateKeyStorePairException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CredentialService {
    private static final Logger log = LoggerFactory.getLogger(CredentialService.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CredentialRepository credentialRepository;
    private final SecretService secretService;
    private final OrganizationCertificateService organizationCertificateService;
    private final ApplicationRepository applicationRepository;

    public CredentialService(CredentialRepository credentialRepository, SecretService secretService, OrganizationCertificateService organizationCertificateService, ApplicationRepository applicationRepository) {
        this.credentialRepository = credentialRepository;
        this.secretService = secretService;
        this.organizationCertificateService = organizationCertificateService;
        this.applicationRepository = applicationRepository;
    }

    public CredentialDao createSharedSecretCredential(CreateCredentialRequestDto dto) throws CredentialCreationFailedException, TooManyCredentialsException, CredentialDuplicateNameException {
        validateIfExists(dto);
        var createSecretDto = new CreateSecretDto();
        createSecretDto.setOrgId(dto.getOrgId());
        createSecretDto.setAppId(dto.getAppId());
        createSecretDto.setDescription(dto.getDescription());
        if (dto.getExpireOn() != null) {
            createSecretDto.setExpireOn(dto.getExpireOn());
        } else {
            Calendar c = Calendar.getInstance();
            c.setTime(new Date());
            c.add(Calendar.YEAR, 1);
            createSecretDto.setExpireOn(c.getTime());
            dto.setExpireOn(c.getTime());
        }
        SecretDao secret;
        try {
            Application app = applicationRepository.getById(createSecretDto.getAppId()).orElseThrow(
                    () -> new AppNotFoundException("Application specified for secret creation does not exist.")
            );
            if (app.getAuthFlow() != AuthFlowEnum.CLIENT_SECRET_JWT) {
                throw new SecretCreationBadRequestException("Application does not support shared secret creation");
            }
            secret = secretService.createSecret(createSecretDto);
        } catch (SecretCreationBadRequestException | SecretCreationFailedException | TooManyCredentialsException |
                 FailedToCreateKeyStorePairException | AppNotFoundException e) {
            throw new CredentialCreationFailedException(e.getMessage(), e);
        }
        dto.setSecretId(secret.getSecret().getId());
        // TODO: Set a real fingerprint
        dto.setFingerprint("TemporaryFingerprint");
        Credential credential;
        try {
            credential = credentialRepository.create(dto);
        } catch (Exception e) {
            secretService.deleteSecret(secret.getSecret().getId());
            throw e;
        }
        return new CredentialDao(credential, secret.getSecretValue());
    }

    public CredentialDao createPrivateKeyCredential(CreateCredentialRequestDto dto) throws CredentialCreationFailedException, TooManyCredentialsException, CredentialDuplicateNameException, CertificateDoesNotExistException, NoActiveCertificateException {
        validateIfExists(dto);
        Optional<CertificateEntity> certificate = organizationCertificateService.get(dto.getOrgId(), dto.getCertificateId());

        if (certificate.isPresent()) {
            if (certificate.get().getStatus().getValue() != CertificateStatus.ACTIVE.getValue()) {
                throw new NoActiveCertificateException("No active certificate found for organization");
            }
            try {
                LocalDate date = LocalDate.parse(certificate.get().getValidTo(), DATE_FORMATTER);
                dto.setExpireOn(java.sql.Date.valueOf(date));
            } catch (Exception e) {
                log.error("Failed to parse certificate valid to date", e);
                throw new CredentialCreationFailedException("Failed to parse certificate valid to date", e);
            }
            dto.setCertId(certificate.get().getCertId());
        } else {
            throw new CertificateDoesNotExistException("Certificate does not exist");
        }

        // TODO: Set a real fingerprint
        dto.setFingerprint("TemporaryFingerprint");
        Credential credential;
        try {
            credential = credentialRepository.create(dto);
        } catch (Exception e) {
            log.error("Failed to create credential", e);
            throw e;
        }
        return new CredentialDao(credential, null);
    }

    private void validateIfExists(CreateCredentialRequestDto dto) throws TooManyCredentialsException, CredentialDuplicateNameException {
        List<Credential> credentials = credentialRepository.getActiveCredentials(dto.getAppId());
        if (credentials.isEmpty()) {
            dto.setCredentialStatus(CredentialStatus.Active);
        } else if (credentials.size() == 1) {
            if (Objects.equals(credentials.getFirst().getName(), dto.getName())) {
                throw new CredentialDuplicateNameException("Credential name already exists");
            }
            dto.setCredentialStatus(CredentialStatus.Active);
        } else {
            throw new TooManyCredentialsException("Maximum number of credentials for application exceeded");
        }
    }

    public List<Credential> getActiveCredentials(Integer appId) {
        return credentialRepository.getActiveCredentials(appId);
    }

    public List<Credential> getAll(Integer orgId, Integer appId) {
        List<Credential> credentials = credentialRepository.getAll(orgId, appId);
        for (Credential credential : credentials) {
            if (credential.getAuthFlow() == AuthFlowEnum.CLIENT_SECRET_JWT) {
                Optional<Secret> secret = secretService.getById(credential.getSecretId());
                if (secret.isPresent()) {
                    credential.setValue(secret.get().getSecretKey());
                    credential.setExpireOn(secret.get().getExpiration());
                }
            } else if (credential.getAuthFlow() == AuthFlowEnum.PRIVATE_KEY_JWT) {
                Optional<CertificateEntity> cert = organizationCertificateService.getById(orgId, credential.getCertificateId());
                if (cert.isPresent()) {
                    credential.setValue(cert.get().getCertificate());
                    credential.setExpireOn(cert.get().getValidTo());
                }
            }
        }
        return credentials;
    }

    @Cacheable(cacheNames = "credential-secrets-by-app", key = "#orgId + '-' + #appId")
    public List<Credential> getAllClientSecretJwt(Integer orgId, Integer appId) {
        log.debug("Loading and decrypting secrets for application: {}:{}", orgId, appId);
        List<Credential> credentials = credentialRepository.getAll(orgId, appId, AuthFlowEnum.CLIENT_SECRET_JWT);
        for (Credential credential : credentials) {
            setSecretValues(credential);
        }
        log.debug("Cached {} credentials with decrypted secrets for application: {}:{}", credentials.size(), orgId, appId);
        return credentials;
    }

    private void setSecretValues(Credential credential) {
        Optional<Secret> secret = secretService.getById(credential.getSecretId());
        if (secret.isPresent()) {
            credential.setValue(secret.get().getSecretKey());
            credential.setExpireOn(secret.get().getExpiration());
        } else {
            log.warn("Secret not found for credential: {} with secretId{}", credential.getId(), credential.getSecretId());
        }
    }

    public boolean isCredentialExpired(Credential credential) {
        if (credential.getExpireOn() == null) {
            return false;
        }

        try {
            LocalDate date = LocalDate.parse(credential.getExpireOn().toString(), DATE_FORMATTER);
            return LocalDate.now().isAfter(date);
        } catch (Exception e) {
            log.error("Failed to parse credential expire on date for credential Id {}: {}", credential.getId(), credential.getExpireOn(), e);
            return true;
        }
    }

    public Credential updateCredential(UpdateCredentialRequestDto dto, Integer orgId, Integer appId, UUID credentialGuid) throws CredentialUpdateFailedException, CredentialExpiredException, TooManyCredentialsException, CredentialNotFoundException, CredentialDuplicateNameException {
        Optional<Credential> credential = credentialRepository.getBuGuid(orgId, appId, credentialGuid);
        boolean active = "Active".equals(dto.getStatus());

        if (credential.isPresent()) {
            if (active && (credential.get().getCredentialStatus() == CredentialStatus.Active)) {
                throw new CredentialUpdateFailedException("Credential is already active");
            }
            if (credential.get().getExpireOn() != null) {
                try {
                    LocalDate date = LocalDate.parse(credential.get().getExpireOn(), DATE_FORMATTER);
                    if (active && LocalDate.now().isAfter(date)) {
                        throw new CredentialExpiredException("Credential has expired");
                    }
                } catch (CredentialExpiredException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("Failed to parse credential expire on date for credential Id {}: {}", credential.get().getId(), credential.get().getExpireOn(), e);
                }
            }
        } else {
            throw new CredentialNotFoundException("Credential not found");
        }
        int status = CredentialStatus.fromString(dto.getStatus()).getValue();
        if (active) {
            List<Credential> credentials = credentialRepository.getActiveCredentials(appId);
            if (credentials.size() == 1) {
                if (Objects.equals(credentials.getFirst().getName(), credential.get().getName())) {
                    throw new CredentialDuplicateNameException("Credential with name" + credential.get().getName() + " already exists");
                }
            } else if (credentials.size() > 1) {
                throw new TooManyCredentialsException("Maximum number of active credentials for application exceeded");
            }
        }
        return credentialRepository.updateStatus(status, orgId, appId, credentialGuid);
    }

    public boolean deleteCredential(Integer orgId, Integer appId, UUID credentialGuid) throws CredentialNotFoundException, CredentialUpdateFailedException, CredentialNotExpiredException {
        Optional<Credential> credential = credentialRepository.getBuGuid(orgId, appId, credentialGuid);
        if (credential.isEmpty()) {
            throw new CredentialNotFoundException("Credential not found");
        }
        Credential cred = credential.get();
        if (cred.getCredentialStatus().equals(CredentialStatus.Inactive)) {
            return true;
        }
        boolean expired = true;
        if (cred.getExpireOn() != null) {
            try {
                LocalDate date = LocalDate.parse(cred.getExpireOn(), DATE_FORMATTER);
                if (date.isAfter(LocalDate.now())) {
                    expired = false;
                }
            } catch (Exception e) {
                log.error("Failed to parse credential expire on date for credential Id {}: {}", cred.getId(), cred.getExpireOn(), e);
                expired = false;
            }
        }
        if (cred.getCredentialStatus().equals(CredentialStatus.Disabled) || expired) {
            Credential deletedCredential = credentialRepository.updateStatus(CredentialStatus.Inactive.getValue(), orgId, appId, credentialGuid);
            return deletedCredential != null;
        } else {
            throw new CredentialNotExpiredException("Credential is not expired");
        }
    }
}

