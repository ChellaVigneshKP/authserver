package com.chellavignesh.authserver.adminportal.organization.dto;

import com.chellavignesh.authserver.adminportal.organization.OrganizationStatus;
import com.chellavignesh.authserver.adminportal.organization.entity.OrganizationGroupPermission;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
@Getter
public class OrganizationGroupPermissionResponseDto {
    private UUID id;
    private String name;
    private String description;
    private String key;
    private OrganizationStatus status;

    public static OrganizationGroupPermissionResponseDto fromOrganizationGroupPermission(OrganizationGroupPermission ogp) {
        return new OrganizationGroupPermissionResponseDto(
                ogp.getRowGuid(),
                ogp.getName(),
                ogp.getDescription(),
                ogp.getKey(),
                ogp.getStatus()
        );
    }
}
