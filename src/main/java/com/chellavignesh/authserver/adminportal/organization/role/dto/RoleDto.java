package com.chellavignesh.authserver.adminportal.organization.role.dto;

import java.util.List;
import java.util.UUID;

public record RoleDto(UUID id, String name, String description, String status,
                      List<String> permissionKeys, int revision, int memberCount) {}
