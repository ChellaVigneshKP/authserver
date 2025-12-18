package com.chellavignesh.authserver.adminportal.user.dto;

import com.chellavignesh.authserver.adminportal.util.ValidPasswordBlackListCheck;
import com.chellavignesh.authserver.adminportal.util.ValidPasswordSyntax;
import com.chellavignesh.authserver.config.OutputMessagesConstants;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@JsonInclude(Include.NON_NULL)
public class BasicUpdateUserPasswordDto implements UpdateUserPasswordDto {

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_PASSWORD)
    @ValidPasswordSyntax(message = OutputMessagesConstants.INVALID_PASSWORD_SYNTAX)
    @ValidPasswordBlackListCheck(message = OutputMessagesConstants.PASSWORD_BLACKLISTED)
    private String password;

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_BRANDING)
    private String branding;
}
