package com.chellavignesh.authserver.adminportal.application;

import com.chellavignesh.authserver.adminportal.application.dto.CreateApplicationDto;
import com.chellavignesh.authserver.adminportal.application.dto.UpdateApplicationDto;
import com.chellavignesh.authserver.adminportal.application.dto.UpdateTokenSettingsDto;
import com.chellavignesh.authserver.adminportal.application.entity.*;
import com.chellavignesh.authserver.adminportal.application.exception.*;
import com.chellavignesh.authserver.adminportal.credential.CredentialService;
import com.chellavignesh.authserver.adminportal.credential.CredentialStatus;
import com.chellavignesh.authserver.adminportal.credential.entity.Credential;
import com.chellavignesh.authserver.adminportal.forgotusername.ForgotUsernameSetting;
import com.chellavignesh.authserver.adminportal.forgotusername.entity.UsernameLookupCriteria;
import com.chellavignesh.authserver.adminportal.organization.OrganizationRepository;
import com.chellavignesh.authserver.adminportal.organization.OrganizationStatus;
import com.chellavignesh.authserver.adminportal.organization.entity.Organization;
import com.chellavignesh.authserver.adminportal.range.RangeCache;
import com.chellavignesh.authserver.enums.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.chellavignesh.authserver.adminportal.application.entity.TokenSettings.MAX_REQUEST_TRANSIT_TIME;

@Service
public class ApplicationService {
    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final List<String> AUTHORIZED_SCOPES = List.of("openid", "profile", "email", "metadata", "idp-admin", "read", "confidential-client");

    private final ApplicationRepository applicationRepository;
    private final CredentialService credentialService;
    private final OrganizationRepository organizationRepository;
    private volatile UpdateTokenSettingsDto updateTokenSettingsDto = null;
    private final TokenSettingsService tokenSettingsService;
    RangeCache rangeCache;

    public ApplicationService(ApplicationRepository applicationRepository,
                              CredentialService credentialService,
                              OrganizationRepository organizationRepository,
                              TokenSettingsService tokenSettingsService,
                              RangeCache rangeCache) {
        this.applicationRepository = applicationRepository;
        this.credentialService = credentialService;
        this.organizationRepository = organizationRepository;
        this.tokenSettingsService = tokenSettingsService;
        this.rangeCache = rangeCache;
    }

    public Application create(Integer orgId, CreateApplicationDto dto) throws AppCreationFailedException {
        if (applicationRepository.existsByName(dto.getName(), orgId)) {
            throw new DataIntegrityViolationException("Application already exists");
        }
        UpdateTokenSettingsDto tokenSettings = getDefaultTokenSettings();
        ForgotUsernameSetting forgotUsernameSetting;
        if (dto.getType() == ApplicationTypeEnum.WEB || dto.getType() == ApplicationTypeEnum.MOBILE) {
            if (dto.getAllowForgotUsername() == null) {
                dto.setAllowForgotUsername(false);
            }
            if (dto.getUsernameType() == null) {
                dto.setUsernameType(UsernameTypeEnum.USERNAME);
            }
            List<UsernameLookupCriteria> criteria = new ArrayList<>();
            Collections.sort(dto.getForgotUsernameSettings());
            for (int i = 0; i < dto.getForgotUsernameSettings().size(); i++) {
                var lookupCriteria = new UsernameLookupCriteria(orgId, null, i, dto.getForgotUsernameSettings().get(i).getLookupCriteria());
                criteria.add(lookupCriteria);
            }
            forgotUsernameSetting = new ForgotUsernameSetting(criteria);
        } else {
            dto.setAllowForgotUsername(null);
            dto.setUsernameType(null);
            List<UsernameLookupCriteria> criteria = new ArrayList<>();
            forgotUsernameSetting = new ForgotUsernameSetting(criteria);
        }

        Application app = applicationRepository.create(orgId, dto, tokenSettings, forgotUsernameSetting, AccessTokenFormatEnum.REFERENCE);
        {
            if (app == null) {
                throw new AppCreationFailedException("Failed to create application");
            }
            return app;
        }
    }

    public boolean updateApplication(Integer orgId, Integer appId, UpdateApplicationDto dto) throws ApplicationDataAccessException {
        Application application = this.getById(appId).get();

        if (!application.getName().equalsIgnoreCase(dto.getName()) && applicationRepository.existsByName(dto.getName(), orgId)) {
            throw new DataIntegrityViolationException("Application name already exists");
        }

        ForgotUsernameSetting forgotUsernameSetting;
        if (application.getType().equals(ApplicationTypeEnum.SERVER)) {
            dto.setAllowForgotUsername(null);
            dto.setUsernameType(null);
            List<UsernameLookupCriteria> criteria = new ArrayList<>();
            forgotUsernameSetting = new ForgotUsernameSetting(criteria);
        } else {
            if (dto.getAllowForgotUsername() == null) {
                dto.setAllowForgotUsername(false);
            }
            if (dto.getUsernameType() == null) {
                dto.setUsernameType(UsernameTypeEnum.USERNAME);
            }
            List<UsernameLookupCriteria> criteria = new ArrayList<>();
            Collections.sort(dto.getForgotUsernameSettings());
            for (int i = 0; i < dto.getForgotUsernameSettings().size(); i++) {
                var lookupCriteria = new UsernameLookupCriteria(orgId, null, i, dto.getForgotUsernameSettings().get(i).getLookupCriteria());
                criteria.add(lookupCriteria);
            }
            forgotUsernameSetting = new ForgotUsernameSetting(criteria);
        }
        boolean result;
        try {
            result = applicationRepository.updateApplication(orgId, appId, dto, forgotUsernameSetting);
        } catch (Exception e) {
            throw new ApplicationDataAccessException("Error while updating application with ID: " + appId + "Organization ID: " + orgId, e);
        }
        return result;
    }

    public boolean updateApplicationUri(Integer orgId, Integer appId, String uri) {
        return applicationRepository.updateApplicationUri(orgId, appId, uri);
    }

    public List<Application> getAll(Integer orgId) {
        return applicationRepository.getAll(orgId);
    }

    public boolean exists(Integer orgId, UUID appId) {
        return applicationRepository.exists(orgId, appId);
    }

    public Optional<Application> get(UUID appId) {
        return applicationRepository.get(appId);
    }

    public boolean inactivateApplication(Integer orgId, Integer appId) throws ApplicationDataAccessException {
        boolean result;
        try {
            result = applicationRepository.inactivateApplication(orgId, appId, false);
        } catch (Exception e) {
            throw new ApplicationDataAccessException("Error while inactivating application with ID: " + appId + "Organization ID: " + orgId, e);
        }
        return result;
    }

    public Optional<Application> getById(Integer appId) {
        return applicationRepository.getById(appId);
    }

    public Optional<ApplicationDetail> getApplicationDetailById(Integer appId) {
        return applicationRepository.getApplicationDetailById(appId);
    }

    public Optional<Application> getByClientId(String clientId) {
        return applicationRepository.getByClientId(clientId);
    }

    public ApplicationSettings getSettingsByApplicationId(Integer appId) throws AppSettingsNotFoundException {
        return applicationRepository.getSettingsByApplicationId(appId);
    }

    public Set<String> getApplicationRedirectUris(Integer appId) {
        return applicationRepository.getApplicationRedirectUris(appId);
    }

    public Set<String> getApplicationLogoutRedirectUris(Integer appId) {
        return applicationRepository.getApplicationLogoutRedirectUris(appId);
    }

    public RegisteredClient getRegisteredClientById(Integer applicationId) throws AppNotFoundException {
        Application application = getById(applicationId).orElseThrow(() -> new AppNotFoundException("Application not found with ID: " + applicationId));
        return applicationToRegisteredClient(application, 0);
    }

    @Cacheable(cacheNames = "registered-client-by-client-id", key = "#clientId")
    public RegisteredClient getRegisteredClientByClientId(String clientId) throws AppNotFoundException {
        log.trace("Fetching registered client for client ID: {}", clientId);
        try {
            Application application = getByClientId(clientId).orElseThrow(() -> {
                log.error("Application not found for client ID: {}", clientId);
                return new AppNotFoundException("Application not found for client ID: " + clientId);
            });

            log.trace("Found application for client ID: {}", clientId);
            log.trace("Application ID: {}", application.getId());
            log.trace("Application name: {}", application.getName());
            log.trace("Application type: {}", application.getType());
            log.trace("Application URI: {}", application.getUri());
            log.trace("Auth Flow: {}", application.getAuthFlow());
            log.trace("Organization ID: {}", application.getOrgId());

            RegisteredClient registeredClient = applicationToRegisteredClient(application, 0);
            if (registeredClient != null) {
                log.trace("Registered client created successfully for client ID: {}", clientId);
            } else {
                log.error("Failed to create registered client for client ID: {}", clientId);
            }
            return registeredClient;
        } catch (AppNotFoundException e) {
            log.error("Application not found for client ID: {} - {}", clientId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to create registered client for client ID: {} - {}", clientId, e.getMessage());
            throw e;
        }
    }

    public RegisteredClient getRegisteredClientByClientId(String clientId, int activeSecretNumber) throws AppNotFoundException {
        Application application = getByClientId(clientId).orElseThrow(() ->
                new AppNotFoundException("Application not found for client ID: " + clientId));
        return applicationToRegisteredClient(application, activeSecretNumber);
    }

    private RegisteredClient applicationToRegisteredClient(Application application, int activeSecretNumber) {
        log.trace("Creating registered client for application ID: {} for client ID:{}", application.getId(), application.getClientId());
        if (!isOrganizationActive(application)) {
            log.error("Organization with ID: {} is inactive", application.getOrgId());
            return null;
        }
        log.trace("Organization with ID: {} is active", application.getOrgId());
        ApplicationSettings settings;
        Set<String> redirectUris;
        com.chellavignesh.authserver.adminportal.application.entity.TokenSettings tokenSettings;
        try {
            log.trace("Fetching application settings for application ID: {}", application.getId());
            settings = getSettingsByApplicationId(application.getId());
            log.trace("ApplicationSettings loaded - RequirePkce: {}, JWSAlgorithm: {}", settings.getRequirePkce(), settings.getJWSAlgorithm());
            tokenSettings = this.tokenSettingsService.getForApp(application.getOrgId(), application.getId()).orElseThrow(TokenSettingsNotFoundException::new);
            if (!settings.getRequirePkce() && settings.getJWSAlgorithm() == null) {
                log.error("Application with ID: {} does not support PKCE", application.getId());
                throw new RegisteredClientMissingJWSAlgorithmException(
                        "JWSAlgorithm for application with ID: " + application.getId() + " is not set" + "Auth server will not treat application as confidential client"
                );
            }
            log.trace("Fetching application redirect URIs for application ID: {}", application.getId());
            redirectUris = getApplicationRedirectUris(application.getId());
            log.trace("Application redirect URIs loaded - Redirect URIs: {}", redirectUris);
        } catch (RegisteredClientMissingJWSAlgorithmException | TokenSettingsNotFoundException |
                 AppSettingsNotFoundException e) {
            log.error("Failed to create registered client for application ID: {} - {}", application.getId(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Failed to create registered client for application ID: {} - {}", application.getId(), e.getMessage(), e);
            return null;
        }

        String clientSecret = getClientSecret(application, activeSecretNumber);
        ClientSettings.Builder clientSettingsBuilder = ClientSettings.builder();
        if (settings.getRequirePkce() && settings.getJWSAlgorithm() == null) {
            log.trace("Application with ID: {} does not support PKCE", application.getId());
            clientSettingsBuilder.requireProofKey(settings.getRequirePkce())
                    .requireAuthorizationConsent(settings.getRequireConsent())
                    .jwkSetUrl(settings.getJWKSetURL().formatted(application.getId()));
        } else {
            log.trace("Application with ID: {} supports PKCE", application.getId());
            clientSettingsBuilder.tokenEndpointAuthenticationSigningAlgorithm(settings.getJWSAlgorithm().toJwsAlgorithm())
                    .requireProofKey(settings.getRequirePkce())
                    .requireAuthorizationConsent(settings.getRequireConsent())
                    .jwkSetUrl(settings.getJWKSetURL().formatted(application.getClientId()));
        }
        RegisteredClient.Builder registeredClientBuilder = RegisteredClient.withId(application.getClientId())
                .clientId(application.getClientId())
                .authorizationGrantTypes(grantTypes -> {
                    if (application.getType() == ApplicationTypeEnum.SERVER) {
                        grantTypes.add(AuthorizationGrantType.CLIENT_CREDENTIALS);
                    } else if (application.getType() == ApplicationTypeEnum.WEB || application.getType() == ApplicationTypeEnum.MOBILE) {
                        grantTypes.add(AuthorizationGrantType.AUTHORIZATION_CODE);
                        grantTypes.add(AuthorizationGrantType.REFRESH_TOKEN);
                    }
                })
                .clientAuthenticationMethods(clientAuthenticationMethods -> {
                    log.trace("Configuring client authentication methods for application ID: {} Authflow:{} Client ID:{}", application.getId(), application.getAuthFlow(), application.getClientId());
                    switch (application.getAuthFlow()) {
                        case CLIENT_SECRET_JWT:
                            log.trace("Client authentication methods configured for application ID: {} Authflow:{} Client ID:{} adding CLIENT_SECRET", application.getId(), application.getAuthFlow(), application.getClientId());
                            clientAuthenticationMethods.add(ClientAuthenticationMethod.CLIENT_SECRET_JWT);
                            break;
                        case PRIVATE_KEY_JWT:
                            log.trace("Client authentication methods configured for application ID: {} Authflow:{} Client ID:{} adding PRIVATE_KEY_JWT", application.getId(), application.getAuthFlow(), application.getClientId());
                            clientAuthenticationMethods.add(ClientAuthenticationMethod.PRIVATE_KEY_JWT);
                            break;
                        case PKCE:
                            log.trace("Client authentication methods configured for application ID: {} Authflow:{} Client ID:{} adding NONE", application.getId(), application.getAuthFlow(), application.getClientId());
                            clientAuthenticationMethods.add(ClientAuthenticationMethod.NONE);
                    }
                })
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                        //TODO: Set key for signing outgoing JWTs
                        .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                        .accessTokenTimeToLive(Duration.ofSeconds(tokenSettings.getAccessTokenTimeToLive()))
                        .deviceCodeTimeToLive(Duration.ofSeconds(tokenSettings.getDeviceCodeTimeToLive()))
                        .refreshTokenTimeToLive(Duration.ofSeconds(tokenSettings.getRefreshTokenTimeToLive()))
                        .setting(MAX_REQUEST_TRANSIT_TIME, tokenSettings.getMaxRequestTransitTime())
                        .build()).clientSettings(clientSettingsBuilder.build()).scopes(scopes -> scopes.addAll(AUTHORIZED_SCOPES)).redirectUris(uris -> uris.addAll(redirectUris)).clientName(application.getName());
        if (application.getAuthFlow() == AuthFlowEnum.CLIENT_SECRET_JWT) {
            log.trace("Adding client secre for CLIENT_SECRET_JWT application ID: {} Authflow:{} Client ID:{}", application.getId(), application.getAuthFlow(), application.getClientId());
            registeredClientBuilder.clientSecret(clientSecret);
        }
        RegisteredClient registeredClient = registeredClientBuilder.build();
        log.trace("Registered client created successfully for application ID: {} for client ID:{}", application.getId(), application.getClientId());
        log.debug("Final Configuration Summary:");
        log.debug("Client ID: {}", registeredClient.getClientId());
        log.debug("Client Name: {}", registeredClient.getClientName());
        log.debug("Auth Methods: {}", registeredClient.getClientAuthenticationMethods());
        log.debug("Grant Types: {}", registeredClient.getAuthorizationGrantTypes());
        log.debug("Redirect URIs: {}", registeredClient.getRedirectUris());
        log.debug("Scopes: {}", registeredClient.getScopes());
        log.debug("Require Proof Key: {}", registeredClient.getClientSettings().isRequireProofKey());
        return registeredClient;
    }

    public List<ApplicationResource> getAllApplicationResources(Integer orgId) {
        return applicationRepository.getAllApplicationResources(orgId);
    }

    public Optional<Resource> getResource(Integer orgId, Integer appId, UUID resourceLibraryGuid) {
        return applicationRepository.getResource(orgId, appId, resourceLibraryGuid);
    }

    public List<ApplicationResource> getAllAssignedResourcesByClientId(String clientId) {
        return applicationRepository.getAllAssignedResourcesByClientId(clientId);
    }

    public Resource assignResource(Integer orgId, Integer appId, Integer resourceLibraryId) throws ResourceCreationFailedException {
        if (this.applicationRepository.resourceExistsForApplication(orgId, appId, resourceLibraryId)) {
            throw new DataIntegrityViolationException("Resource already assigned to application");
        }
        return applicationRepository.assignResource(orgId, appId, resourceLibraryId);
    }

    public boolean deleteResource(Integer resourceId) {
        return applicationRepository.deleteResource(resourceId);
    }

    private String getClientSecret(Application application, int activeSecretNumber) {
        String clientSecret = "ThisIsASecretKeyThatIsAtleast32CharactersLong";
        if (application.getAuthFlow() == AuthFlowEnum.CLIENT_SECRET_JWT && activeSecretNumber > 0) {
            List<Credential> credentials = credentialService.getAllClientSecretJwt(application.getOrgId(), application.getId());
            int i = 1;
            for (Credential credential : credentials) {
                if (credential.getCredentialStatus() == CredentialStatus.Active) {
                    if (checkIfExpire(credential)) {
                        return null;
                    }
                    clientSecret = credential.getValue();
                    if (i == activeSecretNumber) {
                        break;
                    }
                    i++;
                }
            }
        }
        return clientSecret;
    }

    private boolean checkIfExpire(Credential credential) {
        try {
            String expireOn = credential.getExpireOn();
            if (expireOn == null || expireOn.trim().isEmpty()) {
                log.warn("Credential expireOn is null or empty for credential ID: {}", credential.getId());
                return true;
            }
            LocalDate expirationDate;
            if (expireOn.contains("T")) {
                String datePart = expireOn.substring(0, expireOn.indexOf("T"));
                expirationDate = LocalDate.parse(datePart, DATE_FORMATTER);
            } else {
                expirationDate = LocalDate.parse(expireOn, DATE_FORMATTER);
            }
            if (LocalDate.now().isAfter(expirationDate)) {
                log.warn("Credential expireOn is expired for credential ID: {}", credential.getId());
                return true;
            }
        } catch (Exception e) {
            log.error("Error while checking credential expireOn for credential ID: {}", credential.getId(), e);
            return true;
        }
        return false;
    }

    private boolean isOrganizationActive(Application application) {
        Optional<Organization> organization = organizationRepository.getById(application.getOrgId());
        return organization.filter(value -> value.getStatus() != OrganizationStatus.INACTIVE).isPresent();
    }

    UpdateTokenSettingsDto getDefaultTokenSettings() {
        if (updateTokenSettingsDto == null) {
            synchronized (this) {
                if (updateTokenSettingsDto == null) {
                    UpdateTokenSettingsDto temp = new UpdateTokenSettingsDto();
                    temp.setAccessTokenTimeToLive(rangeCache.getRange(RangeTypeEnum.ACCESS_TOKEN_TTL).getMin());
                    temp.setAuthCodeTimeToLive(rangeCache.getRange(RangeTypeEnum.AUTH_CODE_TTL).getMin());
                    temp.setDeviceCodeTimeToLive(rangeCache.getRange(RangeTypeEnum.DEVICE_CODE_TTL).getMin());
                    temp.setRefreshTokenTimeToLive(rangeCache.getRange(RangeTypeEnum.REFRESH_TOKEN_TTL).getMin());
                    temp.setReuseRefreshTokens(false);
                    temp.setMaxRequestTransitTime(rangeCache.getRange(RangeTypeEnum.MAX_REQUEST_TRANSIT_TIME).getMax());
                    updateTokenSettingsDto = temp;
                }
            }
        }
        return updateTokenSettingsDto;
    }
}
