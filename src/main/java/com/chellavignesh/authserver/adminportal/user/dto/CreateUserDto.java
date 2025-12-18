package com.chellavignesh.authserver.adminportal.user.dto;

import com.chellavignesh.authserver.adminportal.util.ValidPasswordBlackListCheck;
import com.chellavignesh.authserver.adminportal.util.ValidPasswordSemantic;
import com.chellavignesh.authserver.adminportal.util.ValidPasswordSyntax;
import com.chellavignesh.authserver.config.OutputMessagesConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@ValidPasswordSemantic(message = OutputMessagesConstants.INVALID_PASSWORD_USAGE, firstName = "", lastName = "", email = "", username = "")
public class CreateUserDto {

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_FIRSTNAME)
    private String firstName;

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_LASTNAME)
    private String lastName;

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_USERNAME)
    private String username;

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_EMAIL)
    @Email(message = OutputMessagesConstants.INVALID_EMAIL_SYNTAX)
    private String email;

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_PASSWORD)
    @ValidPasswordSyntax(message = OutputMessagesConstants.INVALID_PASSWORD_SYNTAX)
    @ValidPasswordBlackListCheck(message = OutputMessagesConstants.PASSWORD_BLACKLISTED)
    private String password;

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_PHONE_NUMBER)
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = OutputMessagesConstants.INVALID_PHONE_SYNTAX)
    private String phoneNumber;

    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = OutputMessagesConstants.INVALID_PHONE_SYNTAX)
    private String secondaryPhoneNumber;

    @NotNull(message = OutputMessagesConstants.REQUIRED_ORG_ID)
    private UUID orgGuid;

    private Integer orgId;

    private UUID groupGuid;

    private Map<String, Object> metaData;

    private UUID memberId;

    private UUID loginId;

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_BRANDING)
    private String branding;

    // Person.Credential.SyncFlag - ExternalSource.SyncFlag
    private Boolean syncFlag;
}
