package com.chellavignesh.authserver.adminportal.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class AssignResourceDto {
    @NotNull(message = "Resource Id required")
    private UUID resourceId;
}
