package com.chellavignesh.authserver.adminportal.certificate;

import com.chellavignesh.authserver.adminportal.certificate.exception.CertificateNotFoundException;
import com.chellavignesh.authserver.adminportal.certificate.exception.FailedToStoreCertificateException;
import com.chellavignesh.authserver.adminportal.util.SecurityUtil;
import com.chellavignesh.authserver.keystore.KeyStoreConfig;
import com.chellavignesh.authserver.keystore.parser.PemKeyStorePairParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.Types;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Slf4j
public class CertificateRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final KeyStoreConfig keyStoreConfig;
    private final PemKeyStorePairParser pemKeyStorePairParser;
    private final SecurityUtil securityUtil;

    public CertificateRepository(NamedParameterJdbcTemplate jdbcTemplate, KeyStoreConfig keyStoreConfig, PemKeyStorePairParser pemKeyStorePairParser, SecurityUtil securityUtil) {
        this.jdbcTemplate = jdbcTemplate;
        this.keyStoreConfig = keyStoreConfig;
        this.pemKeyStorePairParser = pemKeyStorePairParser;
        this.securityUtil = securityUtil;
    }

    public UUID create(Certificate certificate) throws FailedToStoreCertificateException {
        var dao = certificate.toDao(keyStoreConfig.password());
        var parameters = new MapSqlParameterSource();
        parameters.addValue("OrgGuid", dao.orgUuid());
        parameters.addValue("CertificateName", dao.name());
        parameters.addValue("CertificateTypeId", dao.certificateTypeId());
        parameters.addValue("IsX509Certificate", dao.isX509Cert());
        parameters.addValue("KeyStoreBytes", dao.keyStoreBytes());
        parameters.addValue("Status", dao.status());
        parameters.addValue("Fingerprint", dao.fingerprint());
        parameters.addValue("Thumbprint", dao.thumbprint());
        parameters.addValue("ValidFrom", dao.validFrom());
        parameters.addValue("ValidTo", dao.validTo());
        parameters.addValue("PasswordKeyStoreBytes", dao.passwordKeyStoreBytes(), Types.VARBINARY);
        parameters.addValue("PasswordKeyId", dao.passwordKeyId());
        parameters.addValue("Subject", dao.subject());
        parameters.addValue("Issuer", dao.issuer());

        try {
            return jdbcTemplate.execute(
                    "{call Client.SaveCertificate(:OrgGuid, :CertificateName, :CertificateTypeId, :IsX509Certificate, :KeyStoreBytes, :Status, :Fingerprint, :Thumbprint, :Subject, :Issuer, :ValidFrom, :ValidTo, :PasswordKeyStoreBytes, :PasswordKeyId)}",
                    parameters,
                    cs -> {
                        try (ResultSet rs = cs.executeQuery()) {
                            if (rs.next()) {
                                return UUID.fromString(rs.getString("RowGuid"));
                            }
                            return null;
                        }
                    }
            );
        } catch (DataAccessException e) {
            log.error("Failed to store certificate", e);
            throw new FailedToStoreCertificateException("Failed to store certificate", e);
        }
    }

    public List<CertificateEntity> getAllByOrgId(Integer orgId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        return jdbcTemplate.query(
                "{call Partner.GetCertificatesByOrgId(:orgId)}",
                parameters,
                new CertificateRowMapper(pemKeyStorePairParser, keyStoreConfig.password())
        );
    }

    public Optional<CertificateEntity> get(Integer orgId, UUID certId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("certId", certId);
        return jdbcTemplate.query(
                "{call Partner.GetCertificate(:orgId, :certId)}",
                parameters,
                new CertificateRowMapper(pemKeyStorePairParser, keyStoreConfig.password())
        ).stream().findFirst();
    }

    public List<CertificateEntity> getALlByClientIdAndType(String clientId, Integer certificateTypeId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("clientId", clientId);
        parameters.addValue("certTypeId", certificateTypeId);
        return jdbcTemplate.query(
                "{call Partner.GetCertificatesByClientIdAndCertTypeId(:clientId, :certTypeId)}",
                parameters,
                new CertificateRowMapper(pemKeyStorePairParser, keyStoreConfig.password())
        );
    }

    public List<CertificateEntity> getAllByCertTypeId(Integer certificateTypeId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("certTypeId", certificateTypeId);
        return jdbcTemplate.query(
                "{call Partner.GetCertificatesByCertTypeId(:certTypeId)}",
                parameters,
                new CertificateRowMapper(pemKeyStorePairParser, keyStoreConfig.password())
        );
    }

    public Optional<CertificateEntity> getById(Integer orgId, Integer certId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("certId", certId.toString());

        return jdbcTemplate.query(
                "{call Partner.GetCertificateById(:orgId, :certId)}",
                parameters,
                new CertificateRowMapper(pemKeyStorePairParser, keyStoreConfig.password())
        ).stream().findFirst();
    }

    @CacheEvict(cacheNames = "registered-client-by-client-id", allEntries = true)
    public CertificateEntity updateStatus(byte status, Integer orgId, UUID certId) throws CertificateNotFoundException {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("certId", certId);
        parameters.addValue("status", status);
        parameters.addValue("modifiedBy", securityUtil.getTokenUserGuid());
        parameters.addValue("modifiedOn", new Date());

        Optional<Integer> certificateId = jdbcTemplate.query(
                "{call Partner.UpdateCertificateStatus(:orgId, :certId, :status, :modifiedOn, :modifiedBy)}",
                parameters,
                (rs, rowNum) -> rs.getInt("ID")
        ).stream().findFirst();

        if (certificateId.isPresent()) {
            log.info("Certificate with id {} updated with status {}", certificateId.get(), status);
            CertificateEntity cert = new CertificateEntity();
            cert.setCertId(certificateId.get());
            return cert;
        } else {
            throw new CertificateNotFoundException("Failed to update certificate status");
        }
    }
}
