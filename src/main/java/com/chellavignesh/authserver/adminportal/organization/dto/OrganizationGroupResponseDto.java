package com.chellavignesh.authserver.adminportal.organization.dto;

import com.chellavignesh.authserver.adminportal.organization.OrganizationStatus;
import com.chellavignesh.authserver.adminportal.organization.entity.OrganizationGroup;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
@Getter
public class OrganizationGroupResponseDto {
    private UUID id;
    private String name;
    private String description;
    private OrganizationStatus status;

    public static OrganizationGroupResponseDto fromOrganizationGroup(OrganizationGroup og) {
        return new OrganizationGroupResponseDto(
                og.getRowGuid(),
                og.getName(),
                og.getDescription(),
                og.getStatus()
        );
    }
}
