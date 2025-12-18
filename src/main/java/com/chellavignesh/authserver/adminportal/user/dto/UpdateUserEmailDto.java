package com.chellavignesh.authserver.adminportal.user.dto;

import com.chellavignesh.authserver.config.OutputMessagesConstants;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class UpdateUserEmailDto extends NotifiableUpdateDtoBase {

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_EMAIL)
    @Email(message = OutputMessagesConstants.INVALID_EMAIL_SYNTAX)
    private String email;
}
