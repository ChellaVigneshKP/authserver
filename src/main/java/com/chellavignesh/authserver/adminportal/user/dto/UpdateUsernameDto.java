package com.chellavignesh.authserver.adminportal.user.dto;

import com.chellavignesh.authserver.config.OutputMessagesConstants;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateUsernameDto extends NotifiableUpdateDtoBase {

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_USERNAME)
    private String username;
}
