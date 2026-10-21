package com.chellavignesh.authserver.adminportal.user.dto;

import com.chellavignesh.authserver.adminportal.toggle.AccountSyncType;
import com.chellavignesh.authserver.adminportal.user.UserStatus;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.util.UUID;
import java.util.Locale;

@JsonInclude(Include.NON_NULL)
public record UserResponseDto(
        UUID id,
        String email,
        String userName,
        String title,
        String firstName,
        String middleInitial,
        String lastName,
        String suffix,
        String phoneNumber,
        Boolean twoFactorEnabled,
        String groupName,
        UUID memberId,
        UUID loginId,
        UserStatus status,
        String branding,
        String secondaryPhoneNumber,
        UUID organizationId,
        @JsonInclude(Include.ALWAYS) UUID groupId,
        String syncMode
) {

    public static UserResponseDto fromUser(UserDetails userDetails) {
        return fromUser(userDetails, AccountSyncType.NONE);
    }

    public static UserResponseDto fromUser(UserDetails userDetails,
            AccountSyncType syncType) {
        boolean synced = Boolean.TRUE.equals(userDetails.profileSyncFlag()) || Boolean.TRUE.equals(userDetails.credSyncFlag());
        String mode = synced && syncType != AccountSyncType.NONE
                ? syncType.name().toLowerCase(Locale.ROOT) : "local";
        return new UserResponseDto(
                userDetails.rowGuid(),
                userDetails.email(),
                userDetails.username(),
                userDetails.title(),
                userDetails.firstName(),
                userDetails.middleInitial(),
                userDetails.lastName(),
                userDetails.suffix(),
                userDetails.phoneNumber(),
                userDetails.twoFactorEnabled(),
                userDetails.groupName(),
                userDetails.memberId(),
                userDetails.loginId(),
                userDetails.status(),
                userDetails.branding(),
                userDetails.secondaryPhoneNumber(),
                userDetails.orgId(),
                userDetails.groupId(),
                mode
        );
    }
}
