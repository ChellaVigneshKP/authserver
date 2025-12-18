package com.chellavignesh.authserver.adminportal.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChangeProfileViewDto {

    private String branding;

    @JsonProperty(value = "redirect_uri")
    private String redirectUri;

    private String uniteMetadata;
}