package com.chellavignesh.authserver.adminportal.user.dto;

import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserProfileDto {

    public static final CreateUserProfileDto CONFLICT_USERNAME_LOOKUP = new CreateUserProfileDto();

    private String branding;
    private String intent;
    private String timestamp;
    private UUID orgId;
    private Credential credential;
    private Profile profile;
    private Metadata metadata;

    // Person.Credential.syncFlag - ExternalSource.SyncFlag
    private Boolean syncFlag;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Credential {

        private UUID loginId;
        private String username;
        private String passwordHash;
        private String cIndex;

        public String getcIndex() {
            return cIndex;
        }

        public void setcIndex(String cIndex) {
            this.cIndex = cIndex;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Profile {

        private UUID memberId;
        private String firstName;
        private String lastName;
        private String email;
        private String phoneNumber;
        private String secondaryPhoneNumber;
        private String mfaFlag;
        private String idpSyncDttm;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {

        private List<Map<String, String>> idp;
        private List<Map<String, String>> unite;
    }
}