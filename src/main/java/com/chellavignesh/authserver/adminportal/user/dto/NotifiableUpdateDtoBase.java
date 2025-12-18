package com.chellavignesh.authserver.adminportal.user.dto;

import com.chellavignesh.authserver.adminportal.metadata.dto.OutgoingMetadataDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotifiableUpdateDtoBase implements NotifiableUpdateDto {

    private String branding;
    private UpdateIntent intent;
    private OutgoingMetadataDto metadata;
}
