package com.chellavignesh.authserver.token;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.application.exception.AppNotFoundException;
import com.chellavignesh.authserver.adminportal.organization.OrganizationService;
import com.chellavignesh.authserver.adminportal.organization.entity.Organization;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.UserStatus;
import com.chellavignesh.authserver.adminportal.user.entity.UserCredentials;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.chellavignesh.authserver.adminportal.user.exception.UserNotFoundException;
import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.enums.entity.ApplicationTypeEnum;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import com.chellavignesh.authserver.enums.entity.AuthSessionStatusEnum;
import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import com.chellavignesh.authserver.session.AuthSessionService;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.token.dto.CreateTokenDto;
import com.chellavignesh.authserver.token.entity.Token;
import com.chellavignesh.authserver.token.exception.TokenCreationFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

import static com.chellavignesh.authserver.adminportal.application.entity.TokenSettings.MAX_REQUEST_TRANSIT_TIME;

@Service
@Slf4j
public class TokenService {

    private final TokenRepository tokenRepository;
    private final AuthSessionService authSessionService;
    private final ApplicationService applicationService;
    private final UserService userService;
    private final OrganizationService organizationService;
    private final boolean isFingerprintingEnabled;

    @Autowired
    public TokenService(TokenRepository tokenRepository, AuthSessionService authSessionService, ApplicationService applicationService, UserService userService, OrganizationService organizationService, @Value("${toggles.fingerprinting.enabled:true}") boolean isFingerprintingEnabled) {
        this.tokenRepository = tokenRepository;
        this.authSessionService = authSessionService;
        this.applicationService = applicationService;
        this.userService = userService;
        this.organizationService = organizationService;
        this.isFingerprintingEnabled = isFingerprintingEnabled;
    }

    // ============================================
    // TOKEN CREATION
    // ============================================

    public Token create(CreateTokenDto dto) throws TokenCreationFailedException {
        return tokenRepository.create(dto);
    }

    // ============================================
    // TOKEN LOOKUP
    // ============================================

    public Optional<Token> getById(Integer id) {
        return tokenRepository.getById(id);
    }

    public Optional<Token> getByClientId(String clientId) {
        return tokenRepository.getByClientId(clientId);
    }

    public Optional<Token> getByValue(String value, TokenTypeEnum type) {
        return tokenRepository.getByValue(value, type);
    }

    public List<Token> getAllActiveBySessionId(UUID sessionId) {
        return tokenRepository.getAllActiveBySessionId(sessionId);
    }

    // ============================================
    // CLAIMS GENERATION
    // ============================================

    public Map<String, Object> getClaimsForToken(Token token) {
        var attributes = new HashMap<String, Object>();

        // Treat null expiration as expired
        AuthSession session = authSessionService.getBySessionId(token.getSessionId()).orElseThrow();   // TODO: This should be handled better
        if (isTokenActive(token, session)) {
            Integer applicationId;
            RegisteredClient client;
            Application application;
            Organization organization;

            try {
                applicationId = session.getApplicationId();
                application = applicationService.getById(applicationId).get();
                client = applicationService.getRegisteredClientById(applicationId);
                organization = organizationService.getById(application.getOrgId()).get();

            } catch (AppNotFoundException e) {
                throw new RuntimeException(e);  // TODO: Handle better
            }

            // Fingerprint check
            if (isFingerprintingEnabled && client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)) {
                attributes.put(ApplicationConstants.CLIENT_FINGERPRINT, session.getClientFingerprint());
            }

            attributes.put("exp", token.getExpiration().toInstant());
            attributes.put("iat", token.getCreatedOn().toInstant());
            attributes.put("scope", session.getScopes().stream().toList());
            attributes.put("client_id", client.getId());
            attributes.put(MAX_REQUEST_TRANSIT_TIME, client.getTokenSettings().getSetting(MAX_REQUEST_TRANSIT_TIME));

            addUrlPermissions(attributes, application, session, client);

            // Subject-level claims
            if (!Objects.equals(session.getSubjectId(), client.getId())) {
                addUserClaims(attributes, session.getScopes(), session.getSubjectId(), client.getId(), session.getBranding());
                if (!session.getBranding().equals("idp")) {
                    attributes.put("org_guid", organization.getRowGuid());
                }
            } else {
                attributes.put("active", true);
                attributes.put("org_guid", organization.getRowGuid());
            }

        } else {
            attributes.put("active", false);
        }

        return attributes;
    }

    // ============================================
    // TOKEN VALIDATION
    // ============================================

    public boolean isTokenActive(Token token, AuthSession session) {
        return token.getExpiration() != null
                && token.getExpiration().toInstant().isAfter(Instant.now())
                && AuthSessionStatusEnum.ACTIVE.equals(session.getAuthSessionStatus());
    }

    // ============================================
    // USER CLAIM FILLING
    // ============================================

    private void addUserClaims(Map<String, Object> attributes, Set<String> scopes, String subjectId, String clientId, String branding) {

        UserDetails user = userService.getByUsernameAndBranding(subjectId, branding).orElseThrow(); // TODO: handle better
        attributes.put("org_guid", user.orgId());
        if (user.status() == UserStatus.Active) {
            attributes.put("active", true);

            if (scopes.contains("openid")) {
                attributes.put("sub", subjectId);
                attributes.put("group_guid", user.groupId());
                attributes.put("rowguid", user.rowGuid());
            }

            if (scopes.contains("profile")) {
                attributes.put("name", user.firstName() + " " + user.lastName());
                attributes.put("phone_number", user.phoneNumber());
            }

            if (scopes.contains("email")) {
                attributes.put("email", user.email());
            }

            if (scopes.contains("metadata")) {
                attributes.put("metadata", getUserMetadata(user));
            }

            if (scopes.contains("idp-admin")) {
                Map<String, List<String>> permissions = userService.getUserPermissions(user.id());
                for (var entry : permissions.entrySet()) {
                    attributes.put(entry.getKey(), entry.getValue().toArray());
                }
            }

        } else {
            attributes.put("active", false);
        }
    }

    private Map<String, Object> getUserMetadata(UserDetails user) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("uuid_member_id", user.rowGuid());

        Optional<UserCredentials> credentials = userService.getCredentialsByUsername(user.username());

        credentials.ifPresent(creds -> metadata.put("uuid_login_id", creds.getRowGuid()));

        try {
            metadata.putAll(userService.getMetadata(user.rowGuid()));
        } catch (UserNotFoundException _) {
            // No metadata found for this user
        }

        return metadata;
    }

    // ============================================
    // URL PERMISSIONS
    // ============================================

    private void addUrlPermissions(Map<String, Object> attributes, Application application, AuthSession session, RegisteredClient client) {
        if (isConfidentialClient(application) && hasUrlPermissionScope(session.getScopes())) {
            var applicationResources = applicationService.getAllAssignedResourcesByClientId(client.getId());
            List<String> urlPermissions = new ArrayList<>();
            applicationResources.forEach(r -> urlPermissions.add(r.getUri() + " " + r.getAllowedMethod()));
            attributes.put("url-permissions", urlPermissions.toArray());
        }
    }

    private boolean isConfidentialClient(Application app) {
        return ApplicationTypeEnum.SERVER.equals(app.getType()) && !AuthFlowEnum.PKCE.equals(app.getAuthFlow());
    }

    private boolean hasUrlPermissionScope(Set<String> scopes) {
        return scopes.contains("confidential-client");
    }

    // ============================================
    // TOKEN RETRIEVAL BY CLIENT + DATE
    // ============================================

    public List<Token> getTokensByClientIdAndRequestDateTime(final String clientId, final Date requestDate) {
        log.debug("Fetching tokens for clientId: {} and requestDate: {}", clientId, requestDate);
        final var result = tokenRepository.getTokensByClientIdAndRequestDateTime(clientId, requestDate);
        log.debug("For clientId {}, requestDate {} → found {} tokens", clientId, requestDate, result.size());
        return result;
    }
}
