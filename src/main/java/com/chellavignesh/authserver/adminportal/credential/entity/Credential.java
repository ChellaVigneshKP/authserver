package com.chellavignesh.authserver.adminportal.credential.entity;

import com.chellavignesh.authserver.adminportal.credential.CredentialStatus;
import com.chellavignesh.authserver.adminportal.util.DateUtil;
import com.chellavignesh.authserver.enums.entity.AlgorithmEnum;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Credential {
    private Integer id;
    private UUID rowGuid;
    private Integer orgId;
    private Integer appId;
    private String name;
    private Integer secretId;
    private Integer certificateId;
    private AuthFlowEnum authFlow;
    private CredentialStatus credentialStatus;
    private String fingerprint;
    private AlgorithmEnum tokenAlgorithm;
    private String expireOn;
    private String value;

    public static Credential fromResult(ResultSet result) throws SQLException {
        Credential secret = new Credential();
        try {
            secret.setId(result.getInt("CredentialId"));
            secret.setRowGuid(UUID.fromString(result.getString("RowGuid")));
            secret.setOrgId(result.getInt("OrganizationId"));
            secret.setAppId(result.getInt("ApplicationId"));
            secret.setName(result.getString("Name"));
            secret.setSecretId(result.getInt("SecretId"));
            secret.setCertificateId(result.getInt("CertificateId"));
            secret.setAuthFlow(AuthFlowEnum.fromInt(result.getInt("AuthFlowId")));
            secret.setCredentialStatus(CredentialStatus.fromInt(result.getInt("CredentialStatus")));
            secret.setFingerprint(result.getString("Fingerprint"));
            secret.setTokenAlgorithm(AlgorithmEnum.fromInt(result.getInt("TokenAlgorithmId")));
            secret.setExpireOn(DateUtil.getISO8601Date(result.getDate("ExpireOn")));
            return secret;
        } catch (SQLException _) {
            return null;
        }
    }
}
