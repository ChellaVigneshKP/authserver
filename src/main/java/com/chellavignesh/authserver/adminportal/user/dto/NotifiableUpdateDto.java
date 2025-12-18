package com.chellavignesh.authserver.adminportal.user.dto;

import com.chellavignesh.authserver.adminportal.metadata.dto.OutgoingMetadataDto;
import com.chellavignesh.authserver.config.OutputMessagesConstants;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public interface NotifiableUpdateDto {

    @NotEmpty(message = OutputMessagesConstants.REQUIRED_BRANDING)
    String getBranding();

    @NotNull(message = OutputMessagesConstants.REQUIRED_INTENT)
    UpdateIntent getIntent();

    @NotNull(message = OutputMessagesConstants.REQUIRED_METADATA)
    OutgoingMetadataDto getMetadata();
}
