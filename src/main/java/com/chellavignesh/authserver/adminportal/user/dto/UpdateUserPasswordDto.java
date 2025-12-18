package com.chellavignesh.authserver.adminportal.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public interface UpdateUserPasswordDto {
    String getPassword();

    String getBranding();
}
