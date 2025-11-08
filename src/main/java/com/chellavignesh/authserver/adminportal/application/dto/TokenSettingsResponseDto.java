package com.chellavignesh.authserver.adminportal.application.dto;

import com.chellavignesh.authserver.adminportal.application.entity.TokenSettings;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class TokenSettingsResponseDto {
    private UUID orgGuid;
    private UUID appGuid;
    private Integer orgId;
    private Integer appId;
    private Integer authCodeTimeToLive;
    private Integer accessTokenTimeToLive;
    private Integer deviceCodeTimeToLive;
    private Integer refreshTokenTimeToLive;
    private Boolean reuseRefreshTokens;
    private Integer maxRequestTransitTime;

    public static TokenSettingsResponseDto fromTokenSettings(TokenSettings settings, UUID orgGuid, UUID appGuid) {
        return new TokenSettingsResponseDto(
                orgGuid,
                appGuid,
                settings.getOrgId(),
                settings.getAppId(),
                settings.getAuthCodeTimeToLive(),
                settings.getAccessTokenTimeToLive(),
                settings.getDeviceCodeTimeToLive(),
                settings.getRefreshTokenTimeToLive(),
                settings.getReuseRefreshTokens(),
                settings.getMaxRequestTransitTime()
        );
    }
}
