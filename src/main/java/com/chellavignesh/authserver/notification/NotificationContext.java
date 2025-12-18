package com.chellavignesh.authserver.notification;

import com.chellavignesh.authserver.adminportal.externalsource.entity.ExternalSource;
import com.chellavignesh.authserver.adminportal.metadata.dto.OutgoingMetadataDto;
import com.chellavignesh.authserver.adminportal.user.dto.NotifiableUpdateDto;
import com.chellavignesh.authserver.adminportal.user.entity.User;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@Getter
@Builder(access = AccessLevel.PRIVATE)
public class NotificationContext {

    @NotNull
    private final User userEntity;

    @NotNull
    private final ExternalSource branding;

    @NotNull
    private final String intent;

    @NotNull
    private final OutgoingMetadataDto metadata;

    @NotNull
    private final Map<String, String> sourceHeaders;

    @NotNull
    public static NotificationContext createFor(@NotNull NotifiableUpdateDto dto, @NotNull ExternalSource branding, @NotNull final User userEntity, @NotNull Map<String, String> sourceHeaders) {
        return NotificationContext.builder().branding(branding).intent(dto.getIntent().getId()).metadata(dto.getMetadata()).userEntity(userEntity).sourceHeaders(sourceHeaders).build();
    }

    @NotNull
    public static NotificationContext createFor(@NotNull String intent, @NotNull ExternalSource branding, @NotNull final User userEntity, @NotNull Map<String, String> sourceHeaders) {
        return NotificationContext.builder().branding(branding).intent(intent).metadata(OutgoingMetadataDto.createEmpty()).userEntity(userEntity).sourceHeaders(sourceHeaders).build();
    }

    @NotNull
    public static NotificationContext createFor(@NotNull String intent, @NotNull ExternalSource branding, @NotNull final User userEntity, @NotNull OutgoingMetadataDto metadata, @NotNull Map<String, String> sourceHeaders) {
        return NotificationContext.builder().branding(branding).intent(intent).metadata(metadata).userEntity(userEntity).sourceHeaders(sourceHeaders).build();
    }
}
