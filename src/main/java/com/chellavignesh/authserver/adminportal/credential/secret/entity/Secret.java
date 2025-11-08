package com.chellavignesh.authserver.adminportal.credential.secret.entity;

import com.chellavignesh.authserver.adminportal.certificate.exception.InvalidPemException;
import com.chellavignesh.authserver.adminportal.util.DateUtil;
import com.chellavignesh.authserver.keystore.KeyStorePair;
import com.chellavignesh.authserver.keystore.exception.FailedToCreateKeyStoreException;
import com.chellavignesh.authserver.keystore.parser.PemKeyStorePairParser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Secret {
    private static Logger logger = LoggerFactory.getLogger(Secret.class);
    private Integer id;
    private UUID rowGuid;
    private Integer orgId;
    private Integer appId;
    private String description;
    private String secretHash;
    private String expiration;
    private String secretKey;

    public static Secret fromResult(ResultSet result, PemKeyStorePairParser pemKeyStorePairParser, String password) throws SQLException {
        Secret secret = new Secret();
        try {
            secret.setId(result.getInt("SecretId"));
            secret.setRowGuid(UUID.fromString(result.getString("RowGuid")));
            secret.setOrgId(result.getInt("OrganizationId"));
            secret.setAppId(result.getInt("ApplicationId"));
            secret.setDescription(result.getString("Description"));
            secret.setSecretHash(result.getString("SecretHashValue"));
            secret.setExpiration(DateUtil.getISO8601Date(result.getDate("ExpireOn")));
            try {
                KeyStorePair keyStorePair = pemKeyStorePairParser.parse(result.getBytes("KeyStore"), result.getBytes("PasswordKeyStore"), password, result.getString("PasswordKeyId"));
                if (keyStorePair.getSecret().isPresent()) {
                    secret.setSecretKey(keyStorePair.getSecret().get());
                }
            } catch (InvalidPemException | FailedToCreateKeyStoreException | SQLException e) {
                logger.error("Failed to parse secret", e);
            }
        } catch (SQLException e) {
            return null;
        }
        return secret;
    }

}
