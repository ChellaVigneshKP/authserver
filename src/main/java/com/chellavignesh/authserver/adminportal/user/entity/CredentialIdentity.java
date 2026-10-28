package com.chellavignesh.authserver.adminportal.user.entity;

import com.chellavignesh.authserver.adminportal.user.UserStatus;

import java.util.UUID;

/**
 * Exact-login identity for an already-authorized management credential write.
 * Profile status is carried as data instead of filtering the lookup so an otherwise
 * authorized administrator can still rotate credentials of an inactive target. The profile's
 * active organization is carried because the downstream sync payload reads it; profile-group and
 * two-factor fields are absent because nothing on this path reads them.
 */
public record CredentialIdentity(
        int id,
        UUID profileId,
        UUID organizationId,
        UUID loginId,
        String username,
        String branding,
        String firstName,
        String lastName,
        String email,
        UserStatus status,
        boolean credSyncFlag) {

    /**
     * Fills only the {@link UserDetails} positions this write path consumes (identity, names,
     * email, organization, status, credential sync flag). Discriminator, group and profile-sync
     * fields are placeholders and must not be read by future callers of this method.
     */
    public UserDetails toUserDetails() {
        return new UserDetails(
                id, firstName, lastName, username, profileId, email, null, null, "", null, false, null, null,
                profileId, loginId, organizationId, 1, status, branding, null, organizationId, credSyncFlag, false);
    }

    public User toUser() {
        var user = new User();
        user.setId(id);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setUsername(username);
        user.setRowGuid(profileId);
        user.setLoginId(loginId);
        return user;
    }
}
