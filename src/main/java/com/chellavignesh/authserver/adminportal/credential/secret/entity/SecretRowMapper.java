package com.chellavignesh.authserver.adminportal.credential.secret.entity;

import com.chellavignesh.authserver.keystore.parser.PemKeyStorePairParser;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SecretRowMapper implements RowMapper<Secret> {
    PemKeyStorePairParser pemKeyStorePairParser;
    String password;

    public SecretRowMapper(PemKeyStorePairParser pemKeyStorePairParser, String password) {
        this.pemKeyStorePairParser = pemKeyStorePairParser;
        this.password = password;
    }

    @Override
    public Secret mapRow(ResultSet result, int rowNum) throws SQLException {
        return Secret.fromResult(result, pemKeyStorePairParser, password);
    }
}
