package com.chellavignesh.authserver.adminportal.user.dto;

import com.chellavignesh.authserver.adminportal.user.UserStatus;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.util.UUID;

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
        String secondaryPhoneNumber
) {

    public static UserResponseDto fromUser(UserDetails userDetails) {
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
                userDetails.secondaryPhoneNumber()
        );
    }
}
