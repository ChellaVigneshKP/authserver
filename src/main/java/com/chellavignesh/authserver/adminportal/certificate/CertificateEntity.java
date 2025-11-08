package com.chellavignesh.authserver.adminportal.certificate;

import com.chellavignesh.authserver.adminportal.certificate.exception.InvalidPemException;
import com.chellavignesh.authserver.enums.entity.CertificateType;
import com.chellavignesh.authserver.keystore.KeyStorePair;
import com.chellavignesh.authserver.keystore.exception.FailedToCreateKeyStoreException;
import com.chellavignesh.authserver.keystore.parser.PemKeyStorePairParser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.CertificateEncodingException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CertificateEntity {
    private static Logger logger = LoggerFactory.getLogger(CertificateEntity.class);

    private UUID id;
    private String name;
    private CertificateType type;
    private CertificateStatus status;
    private String fingerprint;
    private String thumbprint;
    private String validTo;
    private byte[] keyStoreBytes;
    private byte[] passwordKeyStoreBytes;
    private String passwordKeyId;
    private String certificate;
    private KeyStorePair keyStorePair;
    private Integer certId;

    public static CertificateEntity fromResult(ResultSet appResult, PemKeyStorePairParser pemKeyStorePairParser, String password) throws SQLException {
        CertificateEntity cert = new CertificateEntity();
        try {
            cert.setId(UUID.fromString(appResult.getString("RowGuid")));
            cert.setName(appResult.getString("CertificateName"));
            cert.setType(CertificateType.fromInt(appResult.getInt("CertificateTypeId")));
            cert.setStatus(CertificateStatus.fromByte(appResult.getByte("Status")));
            cert.setFingerprint(appResult.getString("Fingerprint"));
            cert.setThumbprint(appResult.getString("Thumbprint"));
            cert.setValidTo(appResult.getString("ValidTo"));
            cert.setCertId(appResult.getInt("CertificateId"));
            try {
                KeyStorePair keyStorePair = pemKeyStorePairParser.parse(appResult.getBytes("KeyStore"), appResult.getBytes("PasswordKeyStore"), password, appResult.getString("PasswordKeyId"));
                cert.setKeyStorePair(keyStorePair);
                cert.setCertificate(Base64.getEncoder().encodeToString(keyStorePair.getCertificate().get().getEncoded()));
            } catch (InvalidPemException | FailedToCreateKeyStoreException | CertificateEncodingException |
                     SQLException e) {
                logger.error("Failed to parse certificate", e);
            }
        } catch (SQLException e) {
            return null;
        }
        return cert;
    }
}
