package com.chellavignesh.authserver.enums;

import com.chellavignesh.authserver.adminportal.credential.CredentialStatus;
import com.chellavignesh.authserver.enums.entity.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EnumRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public EnumRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public void loadEnumValues() {
        namedParameterJdbcTemplate.query(
                "{call dbo.GetEnums}", (rs, rowNum) -> {
                    AuthFlowEnum.setValues(rs);
                    AlgorithmEnum.setValues(rs);
                    ApplicationTypeEnum.setValues(rs);
                    CertificateType.setValues(rs);
                    TokenTypeEnum.setValues(rs);
                    AuthSessionStatusEnum.setValues(rs);
                    AccessTokenFormatEnum.setValues(rs);
                    SuffixType.setValues(rs);
                    CredentialStatus.setValues(rs);
                    UsernameTypeEnum.setValues(rs);
                    return null;
                }
        );
    }
}
