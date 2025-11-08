package com.chellavignesh.authserver.adminportal.application.dto;

import com.chellavignesh.authserver.enums.entity.UsernameTypeEnum;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class UpdateApplicationDto {
    @NotEmpty(message = "Application name required and may not be empty")
    @Size(max = 255, message = "Application name is too long")
    private String name;

    @Size(max = 1024, message = "Application description is too long")
    private String description;

    private UsernameTypeEnum usernameType;
    private Boolean allowForgotUsername;
    private List<ForgotUsernameSettingDto> forgotUsernameSettings = new ArrayList<>();
    private Integer pinTimeToLive;
}
