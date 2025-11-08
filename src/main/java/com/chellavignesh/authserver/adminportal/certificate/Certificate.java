package com.chellavignesh.authserver.adminportal.certificate;

import com.chellavignesh.authserver.adminportal.certificate.entity.CertificateDao;
import com.chellavignesh.authserver.adminportal.certificate.exception.FailedToStoreCertificateException;
import com.chellavignesh.authserver.enums.entity.CertificateType;
import com.chellavignesh.authserver.keystore.KeyStorePair;
import com.chellavignesh.authserver.keystore.exception.FailedToStoreKeyStoreException;

import java.text.SimpleDateFormat;
import java.util.NoSuchElementException;
import java.util.TimeZone;
import java.util.UUID;

public record Certificate(Integer id, UUID orgId, String name, CertificateType certificateType,
                          CertificateStatus status, KeyStorePair keyStorePair, String fingerprint) {

    public CertificateDao toDao(String password) throws FailedToStoreCertificateException {
        try {
            var keyStorePairDao = keyStorePair.toDao(password);
            var dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            var certificate = keyStorePair.getCertificate().orElseThrow();
            var subject = certificate.getSubjectX500Principal().getName();
            var issuer = certificate.getIssuerX500Principal().getName();
            var validFrom = dateFormat.format(keyStorePair.getCertificate().orElseThrow().getNotBefore());
            var validTo = dateFormat.format(keyStorePair.getCertificate().orElseThrow().getNotAfter());
            return new CertificateDao(
                    id,
                    certificateType.getValue(),
                    orgId.toString(),
                    name,
                    true,
                    keyStorePairDao.mainKeyStoreBytes(),
                    status().getValue(),
                    fingerprint,
                    "Temporary thumbprint",
                    validFrom,
                    validTo,
                    keyStorePairDao.passwordKeyStoreBytes(),
                    keyStorePairDao.passwordAlias(),
                    subject,
                    issuer
            );
        } catch (FailedToStoreKeyStoreException e) {
            throw new FailedToStoreCertificateException("Failed to store KeyStorePair", e);
        } catch (NoSuchElementException e) {
            throw new FailedToStoreCertificateException("Could not find KeyStorePassword in PasswordKeyStore", e);
        }
    }
}
