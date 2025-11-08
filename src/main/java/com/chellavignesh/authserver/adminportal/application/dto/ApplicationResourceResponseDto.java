package com.chellavignesh.authserver.adminportal.application.dto;

import com.chellavignesh.authserver.adminportal.application.entity.ApplicationResource;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class ApplicationResourceResponseDto {
    private UUID id;
    private String name;
    private String description;
    private String uri;
    private String allowedMethod;
    private String urn;
    private Integer resourceId;
    private Integer resourceLibraryId;

    public static ApplicationResourceResponseDto fromApplicationResponse(ApplicationResource a) {
        return new ApplicationResourceResponseDto(
                a.getRowGuid(),
                a.getName(),
                a.getDescription(),
                a.getUri(),
                a.getAllowedMethod(),
                a.getUrn(),
                a.getId(),
                a.getResourceLibraryId());
    }
}
