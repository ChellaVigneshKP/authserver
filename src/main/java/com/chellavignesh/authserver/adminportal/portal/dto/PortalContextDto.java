package com.chellavignesh.authserver.adminportal.portal.dto;

import java.util.List;
import java.util.UUID;

/** Public human identity and navigation hints; Java still authorizes every operation. */
public record PortalContextDto(Subject subject, List<Organization> organizations, List<String> permissions,
                               Capabilities capabilities) {
    public record Subject(UUID userId, UUID loginId, String username, UUID organizationId) {}
    public record Organization(UUID id, String name) {}
    public record Capabilities(boolean roleWrite, boolean userWrite, boolean resourceWrite, String providerMode) {}
}
