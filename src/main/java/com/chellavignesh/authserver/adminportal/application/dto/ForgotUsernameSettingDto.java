package com.chellavignesh.authserver.adminportal.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ForgotUsernameSettingDto implements Comparable<ForgotUsernameSettingDto> {
    private Integer priority;
    private String[] lookupCriteria = {};

    @Override
    public int compareTo(ForgotUsernameSettingDto o) {
        return this.priority.compareTo(o.getPriority());
    }
}
