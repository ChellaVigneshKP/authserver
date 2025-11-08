package com.chellavignesh.authserver.adminportal.application.dto;

import com.chellavignesh.authserver.adminportal.util.BooleanDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateTokenSettingsDto {

    private Integer authCodeTimeToLive;
    private Integer accessTokenTimeToLive;
    private Integer deviceCodeTimeToLive;
    private Integer refreshTokenTimeToLive;

    @NotNull(message = "Cannot be empty")
    @JsonDeserialize(using = BooleanDeserializer.class)
    private Boolean reuseRefreshTokens;
    private Integer maxRequestTransitTime;
}
