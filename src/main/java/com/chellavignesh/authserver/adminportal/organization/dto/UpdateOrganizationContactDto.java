package com.chellavignesh.authserver.adminportal.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateOrganizationContactDto {
    @NotEmpty(message = "Name is required and may not be empty")
    @Size(max = 255, message = "Name is too long")
    private String name;
    @NotEmpty(message = "Email is required and may not be empty")
    @Size(max = 255, message = "Email is too long")
    @Email
    private String email;
    @NotEmpty(message = "Phone number is required and may not be empty")
    @Size(max = 255, message = "Phone number is too long")
    private String phoneNumber;
}
