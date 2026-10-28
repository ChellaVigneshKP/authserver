package com.chellavignesh.authserver.adminportal.hosted;

import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.chellavignesh.authserver.session.entity.AuthSession;

import java.net.URI;
import java.util.UUID;

/** Server-only authorization result. Never pass this object to a view or a logger. */
public record HostedAccountActor(
        AuthSession authSession,
        UUID loginId,
        UUID profileId,
        String branding,
        URI callback,
        URI returnTo,
        UserDetails user) {}
