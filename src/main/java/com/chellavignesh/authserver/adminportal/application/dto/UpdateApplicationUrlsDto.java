package com.chellavignesh.authserver.adminportal.application.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class UpdateApplicationUrlsDto {
    private String applicationUrl;
    private List<String> returnUrls;
    private List<String> logoutUrls;
}
