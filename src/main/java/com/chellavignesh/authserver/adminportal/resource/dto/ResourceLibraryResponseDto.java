package com.chellavignesh.authserver.adminportal.resource.dto;

import com.chellavignesh.authserver.adminportal.resource.entity.ResourceLibrary;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
@Getter
public class ResourceLibraryResponseDto {
    private UUID id;
    private String name;
    private String description;
    private String uri;
    private String allowedMethod;
    private String urn;

    public static ResourceLibraryResponseDto fromResourceLibrary(ResourceLibrary rl) {
        return new ResourceLibraryResponseDto(
                rl.getRowGuid(),
                rl.getName(),
                rl.getDescription(),
                rl.getUri(),
                rl.getAllowedMethod(),
                rl.getUrn()
        );
    }
}
