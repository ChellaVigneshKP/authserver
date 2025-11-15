package com.chellavignesh.authserver.controller;

import com.chellavignesh.authserver.adminportal.application.ApplicationRepository;
import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.RedirectUriService;
import com.chellavignesh.authserver.adminportal.application.PostLogoutRedirectUriService;
import com.chellavignesh.authserver.adminportal.application.TokenSettingsService;
import com.chellavignesh.authserver.adminportal.application.dto.CreateApplicationDto;
import com.chellavignesh.authserver.adminportal.application.dto.UpdateApplicationDto;
import com.chellavignesh.authserver.adminportal.application.dto.UpdateTokenSettingsDto;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.application.entity.ApplicationDetail;
import com.chellavignesh.authserver.adminportal.application.entity.TokenSettings;
import com.chellavignesh.authserver.adminportal.credential.CredentialService;
import com.chellavignesh.authserver.adminportal.forgotusername.ForgotUsernameSetting;
import com.chellavignesh.authserver.adminportal.forgotusername.entity.UsernameLookupCriteria;
import com.chellavignesh.authserver.controller.dto.ClientRequest;
import com.chellavignesh.authserver.controller.dto.ClientResponse;
import com.chellavignesh.authserver.controller.dto.SecretRotationResponse;
import com.chellavignesh.authserver.controller.dto.TokenSettingsResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/clients")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:9080"})
public class ClientManagementController {
    
    private static final Logger logger = LoggerFactory.getLogger(ClientManagementController.class);
    
    @Autowired
    private ApplicationService applicationService;
    
    @Autowired
    private ApplicationRepository applicationRepository;
    
    @Autowired
    private TokenSettingsService tokenSettingsService;
    
    @Autowired
    private RedirectUriService redirectUriService;
    
    @Autowired
    private PostLogoutRedirectUriService postLogoutRedirectUriService;
    
    @Autowired
    private CredentialService credentialService;
    
    /**
     * Get all clients for an organization
     */
    @GetMapping
    public ResponseEntity<List<ClientResponse>> getAllClients(
            @RequestParam(defaultValue = "1") Integer orgId) {
        try {
            List<Application> applications = applicationRepository.getAll(orgId);
            List<ClientResponse> responses = applications.stream()
                    .map(this::mapToClientResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            logger.error("Error fetching clients", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get a specific client by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> getClientById(
            @PathVariable Integer id) {
        try {
            var appOptional = applicationRepository.getApplicationDetailById(id);
            if (appOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            ApplicationDetail appDetail = appOptional.get();
            ClientResponse response = mapDetailToClientResponse(appDetail);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching client by id: " + id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Create a new client
     */
    @PostMapping
    public ResponseEntity<ClientResponse> createClient(
            @Valid @RequestBody ClientRequest request,
            @RequestParam(defaultValue = "1") Integer orgId) {
        try {
            CreateApplicationDto createDto = new CreateApplicationDto();
            createDto.setName(request.getName());
            createDto.setDescription(request.getDescription());
            createDto.setType(request.getApplicationType());
            createDto.setAuthMethod(request.getAuthMethod());
            createDto.setJwkSetUrl(request.getJwkSetUrl());
            createDto.setUsernameType(request.getUsernameType());
            createDto.setAllowForgotUsername(request.getAllowForgotUsername() != null ? request.getAllowForgotUsername() : false);
            createDto.setForgotUsernameSettings(new ArrayList<>());
            
            Application createdApp = applicationService.create(orgId, createDto);
            
            // Add redirect URIs if provided
            if (request.getRedirectUris() != null && !request.getRedirectUris().isEmpty()) {
                for (String uri : request.getRedirectUris()) {
                    redirectUriService.createRedirectUri(orgId, createdApp.getId(), uri);
                }
            }
            
            // Add post logout redirect URIs if provided
            if (request.getPostLogoutRedirectUris() != null && !request.getPostLogoutRedirectUris().isEmpty()) {
                for (String uri : request.getPostLogoutRedirectUris()) {
                    postLogoutRedirectUriService.createPostLogoutRedirectUri(orgId, createdApp.getId(), uri);
                }
            }
            
            // Update token settings if provided
            if (request.getAccessTokenTtl() != null) {
                UpdateTokenSettingsDto tokenSettings = new UpdateTokenSettingsDto();
                tokenSettings.setAccessTokenTimeToLive(request.getAccessTokenTtl());
                tokenSettings.setRefreshTokenTimeToLive(request.getRefreshTokenTtl());
                tokenSettings.setAuthCodeTimeToLive(request.getAuthCodeTtl());
                tokenSettings.setDeviceCodeTimeToLive(request.getDeviceCodeTtl());
                tokenSettings.setReuseRefreshTokens(request.getReuseRefreshTokens() != null ? request.getReuseRefreshTokens() : false);
                tokenSettings.setMaxRequestTransitTime(request.getMaxRequestTransitTime());
                tokenSettingsService.updateTokenSettings(orgId, createdApp.getId(), tokenSettings);
            }
            
            ClientResponse response = mapToClientResponse(createdApp);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Error creating client", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Update an existing client
     */
    @PutMapping("/{id}")
    public ResponseEntity<ClientResponse> updateClient(
            @PathVariable Integer id,
            @Valid @RequestBody ClientRequest request,
            @RequestParam(defaultValue = "1") Integer orgId) {
        try {
            var appOptional = applicationRepository.getById(id);
            if (appOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            UpdateApplicationDto updateDto = new UpdateApplicationDto();
            updateDto.setName(request.getName());
            updateDto.setDescription(request.getDescription());
            updateDto.setUsernameType(request.getUsernameType());
            updateDto.setAllowForgotUsername(request.getAllowForgotUsername());
            
            ForgotUsernameSetting forgotUsernameSetting = new ForgotUsernameSetting(new ArrayList<>());
            applicationRepository.updateApplication(orgId, id, updateDto, forgotUsernameSetting);
            
            // Update token settings if provided
            if (request.getAccessTokenTtl() != null) {
                UpdateTokenSettingsDto tokenSettings = new UpdateTokenSettingsDto();
                tokenSettings.setAccessTokenTimeToLive(request.getAccessTokenTtl());
                tokenSettings.setRefreshTokenTimeToLive(request.getRefreshTokenTtl());
                tokenSettings.setAuthCodeTimeToLive(request.getAuthCodeTtl());
                tokenSettings.setDeviceCodeTimeToLive(request.getDeviceCodeTtl());
                tokenSettings.setReuseRefreshTokens(request.getReuseRefreshTokens());
                tokenSettings.setMaxRequestTransitTime(request.getMaxRequestTransitTime());
                tokenSettingsService.updateTokenSettings(orgId, id, tokenSettings);
            }
            
            Application updatedApp = applicationRepository.getById(id).get();
            ClientResponse response = mapToClientResponse(updatedApp);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating client", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Delete/Deactivate a client
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClient(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "1") Integer orgId) {
        try {
            var appOptional = applicationRepository.getById(id);
            if (appOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            applicationRepository.inactivateApplication(orgId, id, false);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting client", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Rotate client secret
     */
    @PostMapping("/{id}/rotate-secret")
    public ResponseEntity<SecretRotationResponse> rotateSecret(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "1") Integer orgId) {
        try {
            var appOptional = applicationRepository.getById(id);
            if (appOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Application app = appOptional.get();
            // Note: Actual secret rotation would require CredentialService implementation
            SecretRotationResponse response = SecretRotationResponse.builder()
                    .clientId(app.getClientId())
                    .message("Secret rotation functionality requires CredentialService integration")
                    .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error rotating secret", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get redirect URIs for a client
     */
    @GetMapping("/{id}/redirect-uris")
    public ResponseEntity<List<String>> getRedirectUris(@PathVariable Integer id) {
        try {
            Set<String> uris = applicationRepository.getApplicationRedirectUris(id);
            return ResponseEntity.ok(new ArrayList<>(uris));
        } catch (Exception e) {
            logger.error("Error fetching redirect URIs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Add redirect URI to a client
     */
    @PostMapping("/{id}/redirect-uris")
    public ResponseEntity<Void> addRedirectUri(
            @PathVariable Integer id,
            @RequestParam String uri,
            @RequestParam(defaultValue = "1") Integer orgId) {
        try {
            redirectUriService.createRedirectUri(orgId, id, uri);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (Exception e) {
            logger.error("Error adding redirect URI", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Delete redirect URI from a client
     */
    @DeleteMapping("/{id}/redirect-uris")
    public ResponseEntity<Void> deleteRedirectUri(
            @PathVariable Integer id,
            @RequestParam String uri,
            @RequestParam(defaultValue = "1") Integer orgId) {
        try {
            redirectUriService.deleteRedirectUri(orgId, id, uri);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting redirect URI", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    private ClientResponse mapToClientResponse(Application app) {
        Set<String> redirectUris = applicationRepository.getApplicationRedirectUris(app.getId());
        Set<String> postLogoutUris = applicationRepository.getApplicationLogoutRedirectUris(app.getId());
        
        TokenSettingsResponse tokenSettings = null;
        try {
            TokenSettings ts = tokenSettingsService.getTokenSettingsByApplicationId(app.getId());
            tokenSettings = TokenSettingsResponse.builder()
                    .accessTokenTtl(ts.getAccessTokenTimeToLive())
                    .refreshTokenTtl(ts.getRefreshTokenTimeToLive())
                    .authCodeTtl(ts.getAuthCodeTimeToLive())
                    .deviceCodeTtl(ts.getDeviceCodeTimeToLive())
                    .reuseRefreshTokens(ts.getReuseRefreshTokens())
                    .maxRequestTransitTime(ts.getMaxRequestTransitTime())
                    .build();
        } catch (Exception e) {
            logger.warn("Could not fetch token settings for application {}", app.getId());
        }
        
        return ClientResponse.builder()
                .id(app.getId())
                .clientId(app.getClientId())
                .name(app.getName())
                .description(app.getDescription())
                .applicationType(app.getType())
                .authMethod(app.getAuthFlow())
                .uri(app.getUri())
                .active(app.getActive())
                .redirectUris(new ArrayList<>(redirectUris))
                .postLogoutRedirectUris(new ArrayList<>(postLogoutUris))
                .tokenSettings(tokenSettings)
                .build();
    }
    
    private ClientResponse mapDetailToClientResponse(ApplicationDetail appDetail) {
        Set<String> redirectUris = applicationRepository.getApplicationRedirectUris(appDetail.getId());
        Set<String> postLogoutUris = applicationRepository.getApplicationLogoutRedirectUris(appDetail.getId());
        
        TokenSettingsResponse tokenSettings = TokenSettingsResponse.builder()
                .accessTokenTtl(appDetail.getAccessTokenTimeToLive())
                .refreshTokenTtl(appDetail.getRefreshTokenTimeToLive())
                .authCodeTtl(appDetail.getAuthCodeTimeToLive())
                .deviceCodeTtl(appDetail.getDeviceCodeTimeToLive())
                .reuseRefreshTokens(appDetail.getReuseRefreshTokens())
                .maxRequestTransitTime(appDetail.getMaxRequestTransitTime())
                .build();
        
        return ClientResponse.builder()
                .id(appDetail.getId())
                .clientId(appDetail.getClientId())
                .name(appDetail.getName())
                .description(appDetail.getDescription())
                .applicationType(appDetail.getType())
                .authMethod(appDetail.getAuthFlow())
                .uri(appDetail.getUri())
                .active(appDetail.getActive())
                .redirectUris(new ArrayList<>(redirectUris))
                .postLogoutRedirectUris(new ArrayList<>(postLogoutUris))
                .tokenSettings(tokenSettings)
                .build();
    }
}
