package com.chellavignesh.authserver.adminportal.application.dto;


import com.chellavignesh.authserver.adminportal.application.entity.ApplicationDetail;
import com.chellavignesh.authserver.enums.entity.ApplicationTypeEnum;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import com.chellavignesh.authserver.enums.entity.UsernameTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class ApplicationDetailResponseDto {
    private UUID id;
    private UUID orgId;
    private String clientId;
    private String name;
    private String description;
    private String uri;
    private ApplicationTypeEnum type;
    private AuthFlowEnum authenticationMethod;
    private Integer authCodeTimeToLive;
    private Integer accessTokenTimeToLive;
    private Integer deviceCodeTimeToLive;
    private Integer refreshTokenTimeToLive;
    private Integer maxRequestTransitTime;
    private UsernameTypeEnum usernameType;
    private Boolean allowForgotUsername;
    private List<ForgotUsernameSettingResponseDto> forgotUsernameSettings;
    private Integer mfaRealmId;
    private Integer pinTimeToLive;


    public static ApplicationDetailResponseDto fromApplicationDetail(ApplicationDetail a, UUID orgGuid) {
        List<ForgotUsernameSettingResponseDto> forgotUsernameSettings = a.getUsernameLookupCriteria() != null
                ? a.getUsernameLookupCriteria().stream()
                .map(criteria -> new ForgotUsernameSettingResponseDto(criteria.getPriority(), criteria.getLookupCriteria()))
                .toList()
                : List.of();

        return new ApplicationDetailResponseDto(
                a.getRowGuid(),
                orgGuid,
                a.getClientId(),
                a.getName(),
                a.getDescription(),
                a.getUri(),
                a.getType(),
                a.getAuthFlow(),
                a.getAuthCodeTimeToLive(),
                a.getAccessTokenTimeToLive(),
                a.getDeviceCodeTimeToLive(),
                a.getRefreshTokenTimeToLive(),
                a.getMaxRequestTransitTime(),
                a.getUsernameType(),
                a.getAllowForgotUsername(),
                forgotUsernameSettings,
                a.getMfaRealmId(),
                a.getPinTimeToLive()
        );
    }
}
