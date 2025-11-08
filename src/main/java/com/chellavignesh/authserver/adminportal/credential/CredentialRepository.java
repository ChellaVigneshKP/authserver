package com.chellavignesh.authserver.adminportal.credential;

import com.chellavignesh.authserver.adminportal.credential.dto.CreateCredentialRequestDto;
import com.chellavignesh.authserver.adminportal.credential.entity.Credential;
import com.chellavignesh.authserver.adminportal.credential.entity.CredentialRowMapper;
import com.chellavignesh.authserver.adminportal.credential.exception.CredentialCreationFailedException;
import com.chellavignesh.authserver.adminportal.credential.exception.CredentialUpdateFailedException;
import com.chellavignesh.authserver.adminportal.util.SecurityUtil;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CredentialRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SecurityUtil securityUtil;

    @Autowired
    public CredentialRepository(NamedParameterJdbcTemplate jdbcTemplate, SecurityUtil securityUtil) {
        this.jdbcTemplate = jdbcTemplate;
        this.securityUtil = securityUtil;
    }

    @Caching(evict = {
            @CacheEvict(value = "registered-client-by-client-id", key = "#dto.appId"),
            @CacheEvict(value = "credential-secrets-by-app", key = "#dto.orgId + ':' + #dto.appId")
    })
    public Credential create(CreateCredentialRequestDto dto) throws CredentialCreationFailedException {
        SqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("orgId", dto.getOrgId())
                .addValue("appId", dto.getAppId())
                .addValue("name", dto.getName())
                .addValue("secretId", dto.getSecretId())
                .addValue("certificateId", dto.getCertId())
                .addValue("algorithmId", dto.getAlgorithmEnum().getValue())
                .addValue("expiration", dto.getExpireOn())
                .addValue("authFlowId", dto.getAuthFlow().getValue())
                .addValue("fingerprint", dto.getFingerprint())
                .addValue("credentialStatus", dto.getCredentialStatus().getValue());

        Credential credential = jdbcTemplate.queryForObject(
                "{call Client.CreateCredential(:orgId, :appId, :name, :secretId, :certificateId, :algorithmId, :expiration, :authFlowId, :fingerprint, :credentialStatus)}",
                parameters,
                new CredentialRowMapper()
        );
        if (credential == null) {
            throw new CredentialCreationFailedException("Failed to create credential");
        }
        return credential;
    }

    public List<Credential> getActiveCredentials(Integer appId) {
        SqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("appId", appId);
        return jdbcTemplate.query(
                "{call Client.GetActiveCredentials(:appId)}",
                parameters,
                new CredentialRowMapper()
        );
    }

    public List<Credential> getAll(Integer orgId, Integer appId) {
        return jdbcTemplate.query(
                "{call Client.GetCredentials(:orgId, :appId)}",
                new MapSqlParameterSource()
                        .addValue("orgId", orgId)
                        .addValue("appId", appId),
                new CredentialRowMapper()
        );
    }

    public List<Credential> getAll(Integer orgId, Integer appId, AuthFlowEnum authFlow) {
        return jdbcTemplate.query(
                "{call Client.GetCredentialsByAuthFLow(:orgId, :appId, :authFlow)}",
                new MapSqlParameterSource()
                        .addValue("orgId", orgId)
                        .addValue("appId", appId)
                        .addValue("authFlow", authFlow.getValue()),
                new CredentialRowMapper()
        );
    }

    public Optional<Credential> getBuGuid(Integer orgId, Integer appId, UUID credentialGuid) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("appId", appId);
        parameters.addValue("credentialGuid", credentialGuid);
        return jdbcTemplate.query(
                "{call Client.GetCredentialByGuid(:orgId, :appId, :credentialGuid)}",
                parameters,
                new CredentialRowMapper()
        ).stream().findFirst();
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "registered-client-by-client-id", key = "#appId"),
            @CacheEvict(cacheNames = "credential-secrets-by-app", key = "#orgId + ':' + #appId")
    })
    public Credential updateStatus(int status, Integer orgId, Integer appId, UUID credentialGuid) throws CredentialUpdateFailedException {
        var parameters = new MapSqlParameterSource()
                .addValue("credentialGuid", credentialGuid)
                .addValue("status", status)
                .addValue("orgId", orgId)
                .addValue("appId", appId)
                .addValue("modifiedOn", new Date())
                .addValue("modifiedBy", securityUtil.getTokenUserGuid());
        Optional<Integer> credentialId = jdbcTemplate.query(
                "{call Client.UpdateCredentialStatus(:orgId, :appId, :credentialGuid, :status, :modifiedOn, :modifiedBy)}",
                parameters,
                (rs, _) -> rs.getInt("ID")
        ).stream().findFirst();

        if (credentialId.isPresent()) {
            Credential credential = new Credential();
            credential.setId(credentialId.get());
            return credential;
        } else {
            throw new CredentialUpdateFailedException("Failed to update credential status");
        }
    }
}
