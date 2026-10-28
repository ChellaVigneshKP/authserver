package com.chellavignesh.authserver.adminportal.user.entity;

import com.chellavignesh.authserver.adminportal.user.UserStatus;

import java.util.UUID;

/**
 * Exact-login identity for an already-authorized management credential write.
 * Profile status is carried as data instead of filtering the lookup so an otherwise
 * authorized administrator can still rotate credentials of an inactive target.
 * Organization and profile-group fields are absent because the write path never reads them.
 */
public record CredentialIdentity(
        int id,
        UUID profileId,
        UUID loginId,
        String username,
        String branding,
        String firstName,
        String lastName,
        String email,
        UserStatus status,
        boolean credSyncFlag) {

    public UserDetails toUserDetails() {
        return new UserDetails(
                id, firstName, lastName, username, profileId, email, null, null, "", null, false, null, null,
                profileId, loginId, null, 1, status, branding, null, null, credSyncFlag, false);
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
