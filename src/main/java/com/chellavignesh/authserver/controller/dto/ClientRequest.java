package com.chellavignesh.authserver.controller.dto;

import com.chellavignesh.authserver.enums.entity.ApplicationTypeEnum;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import com.chellavignesh.authserver.enums.entity.UsernameTypeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ClientRequest {
    @NotBlank(message = "Client name is required")
    private String name;
    
    private String description;
    
    @NotNull(message = "Application type is required")
    private ApplicationTypeEnum applicationType;
    
    @NotNull(message = "Authentication method is required")
    private AuthFlowEnum authMethod;
    
    private String jwkSetUrl;
    
    private UsernameTypeEnum usernameType;
    
    private Boolean allowForgotUsername;
    
    private List<String> redirectUris;
    
    private List<String> postLogoutRedirectUris;
    
    private List<String> scopes;
    
    private Integer accessTokenTtl;
    
    private Integer refreshTokenTtl;
    
    private Integer authCodeTtl;
    
    private Integer deviceCodeTtl;
    
    private Boolean reuseRefreshTokens;
    
    private Integer maxRequestTransitTime;
}
