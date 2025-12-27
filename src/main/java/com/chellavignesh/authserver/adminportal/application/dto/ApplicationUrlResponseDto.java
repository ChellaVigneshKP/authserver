package com.chellavignesh.authserver.adminportal.application.dto;

import com.chellavignesh.authserver.adminportal.application.entity.Application;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class ApplicationUrlResponseDto {
    private UUID appGuid;
    private UUID orgGuid;
    private String applicationUrl;
    private String[] redirectUrls;
    private String[] logoutRedirectUrls;

    public static ApplicationUrlResponseDto from(UUID orgGuid, UUID appGuid, Application a, Set<String> redirectUrls, Set<String> logoutRedirectUrls) {
        return new ApplicationUrlResponseDto(
                appGuid,
                orgGuid,
                a.getUri(),
                redirectUrls.toArray(String[]::new),
                logoutRedirectUrls.toArray(String[]::new)
        );
    }
}
