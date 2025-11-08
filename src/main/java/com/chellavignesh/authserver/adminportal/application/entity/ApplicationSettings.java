package com.chellavignesh.authserver.adminportal.application.entity;

import com.chellavignesh.authserver.enums.entity.AlgorithmEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApplicationSettings {
    private Integer orgId;
    private Integer appId;
    private String JWKSetURL;
    private AlgorithmEnum JWSAlgorithm;
    private Boolean requireConsent;
    private Boolean requirePkce;
    private Boolean allowPlainTextPkce;

    public static ApplicationSettings fromResult(ResultSet rs) {
        ApplicationSettings settings = new ApplicationSettings();
        try {
            settings.setOrgId(rs.getInt("OrganizationId"));
            settings.setAppId(rs.getInt("ApplicationId"));
            settings.setJWKSetURL(rs.getString("JWKSetURL"));
            settings.setJWSAlgorithm(AlgorithmEnum.fromInt(rs.getInt("JWSAlgorithmId")));
            settings.setRequireConsent(rs.getBoolean("RequireConsent"));
            settings.setRequirePkce(rs.getBoolean("RequirePkce"));
            settings.setAllowPlainTextPkce(rs.getBoolean("AllowPlainTextPkce"));
        } catch (SQLException _) {
            return null;
        }
        return settings;
    }
}
