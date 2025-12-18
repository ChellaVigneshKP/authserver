package com.chellavignesh.authserver.adminportal.user.dto;

import com.chellavignesh.authserver.adminportal.util.ValidConfirmPassword;
import com.chellavignesh.authserver.adminportal.util.ValidPasswordBlackListCheck;
import com.chellavignesh.authserver.adminportal.util.ValidPasswordSyntax;
import com.chellavignesh.authserver.config.OutputMessagesConstants;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@ValidConfirmPassword(message = "Passwords entered do not match", password = "", confirmPassword = "")
public class ResetUserPasswordDto {

    @ValidPasswordSyntax(message = OutputMessagesConstants.INVALID_PASSWORD_SYNTAX)
    @ValidPasswordBlackListCheck(message = OutputMessagesConstants.PASSWORD_BLACKLISTED)
    private String password;

    private String confirmPassword;
}
