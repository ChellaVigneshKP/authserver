package com.chellavignesh.authserver.adminportal.user;

import java.util.Objects;
import java.util.UUID;

/** Internal audit provenance. Never deserialized from an HTTP payload. */
public final class ReactivationAuditActor {
    private final String value;

    private ReactivationAuditActor(String prefix, UUID identity) {
        value = prefix + Objects.requireNonNull(identity);
    }

    public static ReactivationAuditActor human(UUID profile) {
        return new ReactivationAuditActor("", profile);
    }

    public static ReactivationAuditActor machine(UUID application) {
        return new ReactivationAuditActor("client:", application);
    }

    public static ReactivationAuditActor source(UUID externalSource) {
        return new ReactivationAuditActor("source:", externalSource);
    }

    public String value() { return value; }
}
