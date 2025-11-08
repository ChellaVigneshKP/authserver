package com.chellavignesh.authserver.adminportal.application;

import com.chellavignesh.authserver.adminportal.application.entity.MfaExpiry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class MfaExpiryPinTimeRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public MfaExpiryPinTimeRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public MfaExpiry getMfaExpiryPinTime(Integer pinTimeToLive, UUID sessionId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("pinTimeToLive", pinTimeToLive);
        parameters.addValue("sessionId", sessionId);

        return namedParameterJdbcTemplate.queryForObject(
                "{call Client.GetMfaExpiryPinTimeV2(:pinTimeToLive, :sessionId)}",
                parameters,
                (rs, _) -> MfaExpiry.fromResult(rs)
        );
    }
}
