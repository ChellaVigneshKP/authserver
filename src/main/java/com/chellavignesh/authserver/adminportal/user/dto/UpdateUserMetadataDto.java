package com.chellavignesh.authserver.adminportal.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class UpdateUserMetadataDto {

    private Map<String, Object> metaData;
}
