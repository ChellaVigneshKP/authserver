package com.chellavignesh.authserver.adminportal.user.dto;

import com.chellavignesh.authserver.adminportal.metadata.dto.OutgoingMetadataDto;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
public class UserPasswordSyncPayload {

    private String intent = "uniteandidp";
    private String branding;
    private String orgId;
    private String timestamp = Instant.now().toString();
    private OutgoingMetadataDto metadata;
    private UserPasswordSyncDto credential;
    private UserPasswordSyncProfileDto profile;

    @Data
    @NoArgsConstructor
    public static class UserPasswordSyncDto {
        UUID loginId;
        String password;
    }

    @Data
    @NoArgsConstructor
    public static class UserPasswordSyncProfileDto {
        UUID memberId;
    }
}
