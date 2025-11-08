package com.chellavignesh.authserver.adminportal.certificate;

import com.chellavignesh.authserver.adminportal.certificate.exception.CertificateNotFoundException;
import com.chellavignesh.authserver.adminportal.certificate.exception.FailedToCreateFingerprintException;
import com.chellavignesh.authserver.adminportal.certificate.exception.FailedToStoreCertificateException;
import com.chellavignesh.authserver.adminportal.certificate.exception.InvalidFileException;
import com.chellavignesh.authserver.enums.entity.CertificateType;
import com.chellavignesh.authserver.jose.KeyCryptoService;
import com.chellavignesh.authserver.keystore.KeyStorePair;
import com.chellavignesh.authserver.keystore.exception.FailedToCreateKeyStorePairException;
import com.chellavignesh.authserver.keystore.parser.PemKeyStorePairParser;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrganizationCertificateService {
    private static final ThreadLocal<MessageDigest> SHA256_DIGEST_POOL =
            ThreadLocal.withInitial(() -> {
                try {
                    return MessageDigest.getInstance("SHA-256");
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException("SHA-256 algorithm not available", e);
                }
            });

    private final PemKeyStorePairParser keyStorePairParser;
    private final CertificateRepository certificateRepository;

    public OrganizationCertificateService(PemKeyStorePairParser keyStorePairParser, CertificateRepository certificateRepository) {
        this.keyStorePairParser = keyStorePairParser;
        this.certificateRepository = certificateRepository;
    }

    public UUID createCertificate(UUID orgId, String name, String password, CertificateType type, InputStream certificarte) throws FailedToCreateKeyStorePairException, InvalidFileException, FailedToStoreCertificateException, FailedToCreateFingerprintException {
        KeyStorePair keyStorePair = keyStorePairParser.parse(certificarte, password);
        if (type == CertificateType.ORGANIZATION && (keyStorePair.getCertificate().isEmpty() || keyStorePair.getPrivateKey().isEmpty())) {
            throw new InvalidFileException("File does not contain certificate and private key");
        } else if (type == CertificateType.PUBLIC_KEY && keyStorePair.getCertificate().isEmpty()) {
            throw new InvalidFileException("File does not contain certificate");
        } else {
            String fingerprint;
            try {
                var x509Certificate = keyStorePair.getCertificate();
                fingerprint = generateFingerprint(x509Certificate);
            } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
                throw new FailedToCreateFingerprintException("Failed to generate fingerprint", e);
            }
            return certificateRepository.create(new Certificate(null, orgId, name, type, CertificateStatus.ACTIVE, keyStorePair, fingerprint));
        }
    }

    public List<CertificateEntity> getAllByOrgId(Integer orgId) {
        return certificateRepository.getAllByOrgId(orgId);
    }

    public Optional<CertificateEntity> get(Integer orgId, UUID certId) {
        return certificateRepository.get(orgId, certId);
    }

    private List<CertificateEntity> getAllByClientIdAndCertTypeId(String clientId, Integer certTypeId) {
        return certificateRepository.getALlByClientIdAndType(clientId, certTypeId);
    }

    public Optional<CertificateEntity> getById(Integer orgId, Integer certId) {
        return certificateRepository.getById(orgId, certId);
    }

    private String generateFingerprint(Optional<X509Certificate> certificate) throws NoSuchAlgorithmException, CertificateEncodingException {
        MessageDigest md = SHA256_DIGEST_POOL.get();
        md.reset();
        byte[] certificateHash;
        if (certificate.isEmpty()) return "";
        certificateHash = md.digest(certificate.get().getEncoded());
        return this.bytesToHex(certificateHash).toLowerCase();
    }

    private String bytesToHex(byte[] hash) {
        return KeyCryptoService.bytesToHex(hash);
    }

    public byte[] getCertificateInFile(CertificateEntity certificate) {
        String certificateString = certificate.getCertificate();
        final int MAX_FILE_WIDTH = 64;
        int certificateStringLength = certificateString.length();
        StringBuilder certFileString = new StringBuilder(certificateStringLength + (certificateStringLength % MAX_FILE_WIDTH));
        int i = 0;
        while (i < certificateStringLength - MAX_FILE_WIDTH) {
            certFileString.append(certificateString, i, i + MAX_FILE_WIDTH).append("\n");
            i += MAX_FILE_WIDTH;
        }
        certFileString.append(certFileString.substring(i)).append("\n");

        String fileText = "-----BEGIN CERTIFICATE-----\n" + certFileString + "-----END CERTIFICATE-----";
        return fileText.getBytes();
    }

    public String getNameForFile(CertificateEntity certificate) {
        return certificate.getName().replaceAll("[^a-zA-Z0-9]", "_") + ".pem";
    }

    public boolean deleteCertificate(Integer orgId, UUID credentialGuid) throws CertificateNotFoundException {
        Optional<CertificateEntity> certificate = certificateRepository.get(orgId, credentialGuid);
        if (certificate.isEmpty()) {
            throw new CertificateNotFoundException("Certificate not found");
        }
        CertificateEntity cert = certificate.get();
        if (cert.getStatus().equals(CertificateStatus.INACTIVE)) {
            return true;
        }
        CertificateEntity deletedCertificate = certificateRepository.updateStatus(CertificateStatus.INACTIVE.getValue(), orgId, credentialGuid);
        return deletedCertificate != null;
    }
}
