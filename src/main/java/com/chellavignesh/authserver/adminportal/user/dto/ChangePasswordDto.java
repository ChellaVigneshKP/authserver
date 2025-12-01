package com.chellavignesh.authserver.adminportal.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChangePasswordDto {
    private String currentPassword;
    private String newPassword;
    private String confirmPassword;
}
