package com.chellavignesh.authserver.adminportal.user.dto;

import com.chellavignesh.authserver.adminportal.util.*;
import com.chellavignesh.authserver.config.OutputMessagesConstants;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
@ValidConfirmPassword(
        message = OutputMessagesConstants.CONFIRM_PASSWORD_NOT_MATCH,
        password = "",
        confirmPassword = ""
)
@ValidPasswordSemantic(
        message = OutputMessagesConstants.INVALID_PASSWORD_USAGE,
        firstName = "",
        lastName = "",
        email = "",
        username = ""
)
@ValidPasswordPreviousUsages(
        message = OutputMessagesConstants.PASSWORD_RECENT_USAGE,
        password = ""
)
public class ValidateUserPasswordDto {

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_VALIDATION_TYPE)
    @ValidValidationType(message = OutputMessagesConstants.INVALID_VALIDATION_TYPE)
    private String validationType;

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_PASSWORD)
    @ValidPasswordSyntax(
            message = OutputMessagesConstants.INVALID_PASSWORD_SYNTAX,
            isBase64Encoded = false
    )
    @ValidPasswordBlackListCheck(
            message = OutputMessagesConstants.PASSWORD_BLACKLISTED,
            isBase64Encoded = false
    )
    @JsonDeserialize(using = Base64StringDeserializer.class)
    private String password;

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_CONFIRM_PASSWORD)
    @JsonDeserialize(using = Base64StringDeserializer.class)
    private String confirmPassword;

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_FIRSTNAME)
    private String firstName;

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_LASTNAME)
    private String lastName;

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_USERNAME)
    private String username;

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_EMAIL)
    @Email(message = OutputMessagesConstants.INVALID_EMAIL_SYNTAX)
    private String email;

    private UUID userGuid;
}
