package com.chellavignesh.authserver.adminportal.user.dto;

import com.chellavignesh.authserver.adminportal.metadata.dto.OutgoingMetadataDto;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
public class UserProfileSyncPayload {

    private String intent = "unite";
    private String branding;
    private String orgId;
    private String timestamp = Instant.now().toString();
    private OutgoingMetadataDto metadata;
    private UserProfileSyncDto profile;

    @Data
    @NoArgsConstructor
    public static class UserProfileSyncDto {
        UUID memberId;
        String firstName;
        String lastName;
        String email;
        String phoneNumber;
        String secondaryPhoneNumber;
    }
}
