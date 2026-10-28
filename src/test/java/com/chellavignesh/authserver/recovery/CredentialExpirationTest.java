package com.chellavignesh.authserver.recovery;

import com.chellavignesh.authserver.adminportal.credential.CredentialExpiration;
import com.chellavignesh.authserver.adminportal.credential.entity.Credential;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialExpirationTest {
    @Test
    void eitherMalformedImportedBoundFailsClosed() {
        String valid = "2035-06-01T18:00:00Z";
        for (String effective : new String[]{
                CredentialExpiration.earliest("malformed-import", valid),
                CredentialExpiration.earliest(valid, "malformed-import")}) {
            var credential = new Credential();
            credential.setExpireOn(effective);
            assertThat(CredentialExpiration.isExpired(credential,
                    Clock.fixed(Instant.parse("2035-06-01T12:00:00Z"), ZoneOffset.UTC))).isTrue();
        }
    }

    @Test
    void effectiveBoundUsesUtcOffsetsAndRetainsMissingValues() {
        String early = "2035-06-01T19:00:00+01:00";
        String late = "2035-06-01T18:30:00Z";
        assertThat(CredentialExpiration.earliest(early, late)).isEqualTo(early);
        assertThat(CredentialExpiration.earliest(late, early)).isEqualTo(early);
        assertThat(CredentialExpiration.earliest(null, early)).isEqualTo(early);
        assertThat(CredentialExpiration.earliest(early, null)).isEqualTo(early);
        assertThat(CredentialExpiration.earliest(null, null)).isNull();
    }
}
