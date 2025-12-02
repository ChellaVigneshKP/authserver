package com.chellavignesh.authserver.mfa.mfarealm;

import com.chellavignesh.authserver.mfa.mfarealm.entity.MFARealm;
import com.chellavignesh.authserver.mfa.mfarealm.entity.MFARealmRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MFARealmRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public MFARealmRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<MFARealm> getAll() {
        return namedParameterJdbcTemplate.query(
                "{call Client.GetMFARealms}",
                new MapSqlParameterSource(),
                new MFARealmRowMapper()
        );
    }
}
