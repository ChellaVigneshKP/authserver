package com.chellavignesh.authserver.adminportal.organization.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateOrganizationDto {
    @NotEmpty(message = "Name is required and may not be empty")
    @Size(max = 255, message = "Name is too long")
    private String name;
    @Size(max = 1024, message = "Description is too long")
    private String description;
    @NotEmpty(message = "Status is required and may not be empty")
    @Pattern(regexp = "Active|Revoked", flags = Pattern.Flag.CASE_INSENSITIVE, message = "Status must be either Active or Revoked")
    private String status;
}
