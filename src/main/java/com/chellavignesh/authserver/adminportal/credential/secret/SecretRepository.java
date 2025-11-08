package com.chellavignesh.authserver.adminportal.credential.secret;

import com.chellavignesh.authserver.adminportal.credential.secret.dto.CreateSecretDto;
import com.chellavignesh.authserver.adminportal.credential.secret.entity.Secret;
import com.chellavignesh.authserver.adminportal.credential.secret.entity.SecretRowMapper;
import com.chellavignesh.authserver.adminportal.credential.secret.exception.SecretCreationFailedException;
import com.chellavignesh.authserver.keystore.KeyStoreConfig;
import com.chellavignesh.authserver.keystore.parser.PemKeyStorePairParser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SecretRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final KeyStoreConfig keyStoreConfig;
    private final PemKeyStorePairParser pemKeyStorePairParser;

    public SecretRepository(NamedParameterJdbcTemplate jdbcTemplate, KeyStoreConfig keyStoreConfig, PemKeyStorePairParser pemKeyStorePairParser) {
        this.jdbcTemplate = jdbcTemplate;
        this.keyStoreConfig = keyStoreConfig;
        this.pemKeyStorePairParser = pemKeyStorePairParser;
    }

    public Secret create(CreateSecretDto dto) throws SecretCreationFailedException {
        SqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("orgId", dto.getOrgId())
                .addValue("appId", dto.getAppId())
                .addValue("description", dto.getDescription())
                .addValue("secretHash", dto.getSecretHashValue())
                .addValue("expiration", dto.getExpireOn())
                .addValue("mainKeyStoreBytes", dto.getMainKeyStoreBytes())
                .addValue("passwordKeyStoreBytes", dto.getPasswordKeyStoreBytes())
                .addValue("passwordKeyId", dto.getPasswordKeyId());

        Secret secret = jdbcTemplate.queryForObject(
                "{call Client.CreateSecret(:orgId, :appId, :description, :secretHash, :expiration, :mainKeyStoreBytes, :passwordKeyStoreBytes, :passwordKeyId)}",
                parameters,
                new SecretRowMapper(pemKeyStorePairParser, keyStoreConfig.password())
        );

        if (secret == null) {
            throw new SecretCreationFailedException("Failed to create secret");
        }

        return secret;
    }

    public Optional<Secret> getById(Integer secretId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("secretId", secretId);
        Secret secret = jdbcTemplate.queryForObject(
                "{call Client.GetSecretById(:secretId)}",
                parameters,
                new SecretRowMapper(pemKeyStorePairParser, keyStoreConfig.password())
        );
        return Optional.ofNullable(secret);
    }

    public List<Secret> getAllSecrets(Integer orgId, Integer appId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("appId", appId);
        return jdbcTemplate.query(
                "{call Client.GetSecrets(:orgId, :appId)}",
                parameters,
                new SecretRowMapper(pemKeyStorePairParser, keyStoreConfig.password())
        );
    }

    public boolean exists(Integer orgId, Integer appId, UUID secretGuid) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("appId", appId);
        parameters.addValue("secretId", secretGuid.toString());
        Integer secretIdFound = jdbcTemplate.queryForObject(
                "{call Client.SecretExists(:orgId, :appId, :secretGuid)}",
                parameters,
                Integer.class
        );
        return secretIdFound != null;
    }

    public void delete(Integer secretId) {
        jdbcTemplate.update(
                "{call Client.DeleteSecret(:secretId)}",
                new MapSqlParameterSource().addValue("secretId", secretId)
        );
    }

}
