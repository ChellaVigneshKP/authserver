package com.chellavignesh.authserver.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenSettingsResponse {
    private Integer accessTokenTtl;
    private Integer refreshTokenTtl;
    private Integer authCodeTtl;
    private Integer deviceCodeTtl;
    private Boolean reuseRefreshTokens;
    private Integer maxRequestTransitTime;
}
