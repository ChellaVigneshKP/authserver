package com.chellavignesh.authserver.adminportal.application.dto;

import com.chellavignesh.authserver.enums.entity.ApplicationTypeEnum;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import com.chellavignesh.authserver.enums.entity.UsernameTypeEnum;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class CreateApplicationDto {
    private String name;
    private String description;
    private ApplicationTypeEnum type;
    private AuthFlowEnum authMethod;
    private String jwkSetUrl;
    private UsernameTypeEnum usernameType;
    private Boolean allowForgotUsername;
    private List<ForgotUsernameSettingDto> forgotUsernameSettings = new ArrayList<>();
}
