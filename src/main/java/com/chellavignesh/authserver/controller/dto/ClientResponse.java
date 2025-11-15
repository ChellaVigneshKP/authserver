package com.chellavignesh.authserver.controller.dto;

import com.chellavignesh.authserver.enums.entity.ApplicationTypeEnum;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientResponse {
    private Integer id;
    private String clientId;
    private String name;
    private String description;
    private ApplicationTypeEnum applicationType;
    private AuthFlowEnum authMethod;
    private String uri;
    private Boolean active;
    private List<String> redirectUris;
    private List<String> postLogoutRedirectUris;
    private List<String> scopes;
    private TokenSettingsResponse tokenSettings;
}
