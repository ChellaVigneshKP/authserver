package com.chellavignesh.authserver.adminportal.application;

import com.chellavignesh.authserver.adminportal.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public class RedirectUriRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final SecurityUtil securityUtil;

    @Autowired
    public RedirectUriRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate, SecurityUtil securityUtil) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.securityUtil = securityUtil;
    }

    public boolean updateRedirectUri(Integer orgId, Integer appId, String uri) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("appId", appId);
        parameters.addValue("uri", uri);
        namedParameterJdbcTemplate.update(
                "{call Client.UpdateRedirectUri(:orgId, :appId, :uri)}",
                parameters);
        return true;
    }

    public boolean createRedirectUris(Integer orgId, Integer appId, List<String> uris) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("appId", appId);
        namedParameterJdbcTemplate.update(
                "{call Client.DeleteRedirectUri(:orgId, :appId)}",
                parameters);
        for (String uri : uris) {
            parameters = new MapSqlParameterSource();
            parameters.addValue("orgId", orgId);
            parameters.addValue("appId", appId);
            parameters.addValue("uri", uri);
            parameters.addValue("modifiedOn", new Date());
            parameters.addValue("modifiedBy", securityUtil.getTokenUserGuid());
            namedParameterJdbcTemplate.update(
                    "{call Client.CreateRedirectUri(:orgId, :appId, :uri, :modifiedOn, :modifiedBy)}",
                    parameters);
        }
        return true;
    }
}
