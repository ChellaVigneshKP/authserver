package com.chellavignesh.authserver.session.sso;

import java.util.UUID;

public record SingleSignOnCookie(
        UUID sessionId,
        byte[] encryptedSessionId,
        byte[] encryptionKey
) {
}
