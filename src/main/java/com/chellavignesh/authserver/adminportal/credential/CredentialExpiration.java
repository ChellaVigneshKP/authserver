package com.chellavignesh.authserver.adminportal.credential;

import com.chellavignesh.authserver.adminportal.credential.entity.Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.*;
import java.time.format.DateTimeParseException;

/** SQL datetime2 and imported dates without an offset represent UTC. */
public final class CredentialExpiration {
    private static final Logger log = LoggerFactory.getLogger(CredentialExpiration.class);
    private CredentialExpiration() {}

    public static Instant parse(String value) {
        String normalized = value.replace(' ', 'T');
        try {
            return OffsetDateTime.parse(normalized).toInstant();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException dateOnly) {
                return LocalDate.parse(normalized).atStartOfDay(ZoneOffset.UTC).toInstant();
            }
        }
    }

    public static boolean isExpired(Credential credential, Clock clock) {
        if (credential.getExpireOn() == null) return false;
        try {
            return !parse(credential.getExpireOn()).isAfter(clock.instant());
        } catch (DateTimeParseException invalid) {
            // Do not include imported data or exception text in logs.
            log.warn("Invalid credential expiration; treating credential {} as expired", credential.getRowGuid());
            return true;
        }
    }
}
