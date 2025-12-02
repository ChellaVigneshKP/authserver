package com.chellavignesh.authserver.mfa.mfarealm.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class MFARealmRowMapper implements RowMapper<MFARealm> {
    public MFARealm mapRow(ResultSet rs, int rowNum) throws SQLException {
        return MFARealm.fromResult(rs);
    }
}
