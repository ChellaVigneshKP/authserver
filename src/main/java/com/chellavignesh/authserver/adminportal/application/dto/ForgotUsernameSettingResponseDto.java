package com.chellavignesh.authserver.adminportal.application.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class ForgotUsernameSettingResponseDto {
    private int priority;
    private String[] lookupCriteria = {};
}
