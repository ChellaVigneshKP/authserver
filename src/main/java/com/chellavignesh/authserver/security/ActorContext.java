package com.chellavignesh.authserver.security;

import java.util.Set;
import java.util.UUID;

/** The bearer identity remains fixed when a platform administrator selects a tenant. */
public record ActorContext(UUID userId, UUID organizationId, boolean platformAdmin, Set<String> permissions) {
    public ActorContext {
        permissions = Set.copyOf(permissions);
    }
}
