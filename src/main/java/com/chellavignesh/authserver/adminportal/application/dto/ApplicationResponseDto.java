package com.chellavignesh.authserver.adminportal.application.dto;

import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.enums.entity.ApplicationTypeEnum;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import com.chellavignesh.authserver.enums.entity.UsernameTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class ApplicationResponseDto {
    private UUID id;
    private UUID orgId;
    private String clientId;
    private String name;
    private String description;
    private String uri;
    private ApplicationTypeEnum type;
    private AuthFlowEnum authFlow;
    private UsernameTypeEnum usernameType;
    private Boolean allowForgotUsername;

    public static ApplicationResponseDto fromApplication(Application a, UUID orgGuid) {
        return new ApplicationResponseDto(
                a.getRowGuid(),
                orgGuid,
                a.getClientId(),
                a.getName(),
                a.getDescription(),
                a.getUri(),
                a.getType(),
                a.getAuthFlow(),
                a.getUsernameType(),
                a.getAllowForgotUsername()
        );
    }
}
