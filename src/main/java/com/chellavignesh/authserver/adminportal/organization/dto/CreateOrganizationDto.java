package com.chellavignesh.authserver.adminportal.organization.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateOrganizationDto {
    private String name;
    private String description;
    private Integer primaryContactId;
    private Integer secondaryContactId;
}
