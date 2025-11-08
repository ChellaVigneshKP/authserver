package com.chellavignesh.authserver.adminportal.application.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenSettings {
    public static String MAX_REQUEST_TRANSIT_TIME = "max_request_transit_time";

    private Integer id;
    private Integer orgId;
    private Integer appId;
    private Integer authCodeTimeToLive;
    private Integer accessTokenTimeToLive;
    private Integer deviceCodeTimeToLive;
    private Integer refreshTokenTimeToLive;
    private Boolean reuseRefreshTokens;
    private Integer maxRequestTransitTime;

    public static TokenSettings fromResult(ResultSet result) {
        TokenSettings settings = new TokenSettings();
        try {
            settings.setId(result.getInt("TokenSettingId"));
            settings.setOrgId(result.getInt("OrganizationId"));
            settings.setAppId(result.getInt("ApplicationId"));
            settings.setAuthCodeTimeToLive(result.getInt("AuthCodeTimeToLive"));
            settings.setAccessTokenTimeToLive(result.getInt("AccessTokenTimeToLive"));
            settings.setDeviceCodeTimeToLive(result.getInt("DeviceCodeTimeToLive"));
            settings.setRefreshTokenTimeToLive(result.getInt("RefreshTokenTimeToLive"));
            settings.setReuseRefreshTokens(result.getBoolean("ReuseRefreshTokens"));
            settings.setMaxRequestTransitTime(result.getInt("MaxRequestTransitTime"));
        } catch (SQLException _) {
            return null;
        }
        return settings;
    }
}
