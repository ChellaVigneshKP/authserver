package com.chellavignesh.authserver.adminportal.organization.role.dto;

import java.util.List;

public record UpdateRoleDto(String name, String description, List<String> permissionKeys, Integer revision) {}
