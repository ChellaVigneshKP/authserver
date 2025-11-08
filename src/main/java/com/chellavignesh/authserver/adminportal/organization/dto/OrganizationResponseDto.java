package com.chellavignesh.authserver.adminportal.organization.dto;

import com.chellavignesh.authserver.adminportal.organization.OrganizationStatus;
import com.chellavignesh.authserver.adminportal.organization.entity.Contact;
import com.chellavignesh.authserver.adminportal.organization.entity.Organization;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
@Getter
public class OrganizationResponseDto {
    private UUID id;
    private String name;
    private OrganizationStatus status;
    private String description;
    private Contact primaryContact;
    private Contact secondaryContact;

    public static OrganizationResponseDto fromOrganization(Organization organization) {
        return new OrganizationResponseDto(
                organization.getRowGuid(),
                organization.getName(),
                organization.getStatus(),
                organization.getDescription(),
                organization.getPrimaryContact(),
                organization.getSecondaryContact());
    }

}
