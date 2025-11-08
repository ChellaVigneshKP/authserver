package com.chellavignesh.authserver.adminportal.application;

import com.chellavignesh.authserver.adminportal.util.SecurityUtil;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public class PostLogoutRedirectUriRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SecurityUtil securityUtil;

    public PostLogoutRedirectUriRepository(NamedParameterJdbcTemplate jdbcTemplate, SecurityUtil securityUtil) {
        this.jdbcTemplate = jdbcTemplate;
        this.securityUtil = securityUtil;
    }

    public boolean updatePostLogoutRedirectUri(Integer orgId, Integer appId, String uri) {
        var parameters = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("appId", appId);
        parameters.addValue("uri", uri);
        parameters.addValue("modifiedOn", new java.util.Date());
        parameters.addValue("modifiedBy", securityUtil.getTokenUserGuid());
        jdbcTemplate.update(
                "{call dbo.UpdatePostLogoutRedirectUri(:orgId, :appId, :uri, :modifiedOn, :modifiedBy)}",
                parameters
        );
        return true;
    }

    public boolean createPostLogoutRedirectUris(Integer orgId, Integer appId, List<String> uris) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("appId", appId);
        jdbcTemplate.update("{call CLient.DeletePostLogoutRedirectUris(:orgId, :appId)}", parameters);

        for (String uri : uris) {
            parameters = new MapSqlParameterSource();
            parameters.addValue("orgId", orgId);
            parameters.addValue("appId", appId);
            parameters.addValue("uri", uri);
            parameters.addValue("modifiedOn", new Date());
            parameters.addValue("modifiedBy", securityUtil.getTokenUserGuid());
            jdbcTemplate.update(
                    "{call dbo.CreatePostLogoutRedirectUris(:orgId, :appId, :uris, :createdOn, :createdBy)}",
                    parameters
            );
        }
        return true;
    }
}
