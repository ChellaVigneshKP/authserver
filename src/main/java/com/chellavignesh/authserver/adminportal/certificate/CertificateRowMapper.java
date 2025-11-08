package com.chellavignesh.authserver.adminportal.certificate;

import com.chellavignesh.authserver.keystore.parser.PemKeyStorePairParser;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class CertificateRowMapper implements RowMapper<CertificateEntity> {
    PemKeyStorePairParser pemKeyStorePairParser;
    String password;

    public CertificateRowMapper(PemKeyStorePairParser pemKeyStorePairParser, String password) {
        this.pemKeyStorePairParser = pemKeyStorePairParser;
        this.password = password;
    }

    @Override
    public CertificateEntity mapRow(ResultSet result, int rowNum) throws SQLException {
        return CertificateEntity.fromResult(result, pemKeyStorePairParser, password);
    }
}
