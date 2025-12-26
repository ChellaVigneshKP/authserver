package com.chellavignesh.authserver.session;

import com.chellavignesh.authserver.adminportal.externalsource.ExternalSourceService;
import com.chellavignesh.authserver.adminportal.externalsource.exception.InvalidBrandingException;
import com.chellavignesh.authserver.adminportal.forgotusername.exception.ConflictUsernameLookupSearchException;
import com.chellavignesh.authserver.adminportal.forgotusername.exception.UniqueUsernameNotfoundException;
import com.chellavignesh.authserver.adminportal.organization.OrganizationService;
import com.chellavignesh.authserver.adminportal.organization.exception.OrgNotFoundException;
import com.chellavignesh.authserver.adminportal.user.CreateUserNotificationConfig;
import com.chellavignesh.authserver.adminportal.user.OnPremAccountServiceClient;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.UserStatus;
import com.chellavignesh.authserver.adminportal.user.dto.CreateUserProfileDto;
import com.chellavignesh.authserver.adminportal.user.dto.UserApprovalStatusDto;
import com.chellavignesh.authserver.adminportal.user.entity.UserAuthDetails;
import com.chellavignesh.authserver.adminportal.user.exception.UserCreationFailedException;
import com.chellavignesh.authserver.adminportal.user.exception.UserSyncFailedException;
import com.chellavignesh.authserver.adminportal.user.mapper.UserDtoMapper;
import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.session.dto.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;
    private final OnPremAccountServiceClient onPremAccountServiceClient;
    private final ExternalSourceService externalSourceService;
    private final boolean userDetailsTryHarderEnabled;
    private final OrganizationService organizationService;

    private final HttpServletRequest authRequest;

    private final HttpServletResponse authResponse;

    private final RequestCache requestCache = new HttpSessionRequestCache();

    public CustomUserDetailsService(UserService userService, OnPremAccountServiceClient onPremAccountServiceClient, ExternalSourceService externalSourceService, @Value("${toggles.login.try-harder-enabled}") boolean userDetailsTryHarderEnabled, OrganizationService organizationService, HttpServletRequest authRequest, HttpServletResponse authResponse) {
        this.userService = userService;
        this.onPremAccountServiceClient = onPremAccountServiceClient;
        this.externalSourceService = externalSourceService;
        this.userDetailsTryHarderEnabled = userDetailsTryHarderEnabled;
        this.organizationService = organizationService;
        this.authRequest = authRequest;
        this.authResponse = authResponse;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        Optional<UserAuthDetails> userCredentials;
        final var usernameBrandArray = username.split(System.lineSeparator());

        if (ArrayUtils.isEmpty(usernameBrandArray) || usernameBrandArray.length < 2 || StringUtils.isBlank(usernameBrandArray[1])) {

            log.warn("Username is missing line separator separating brand from login, blocking login");
            throw new UsernameNotFoundException("Username is missing brand parameter, cannot continue");
        }

        log.debug("After brand extraction username is: {}, brand is: {}", usernameBrandArray[0], usernameBrandArray[1]);

        userCredentials = userService.getUserAuthDetailsByUsernameAndExternalSourceCode(usernameBrandArray[0], usernameBrandArray[1]);

        if ((userCredentials.isEmpty() || userCredentials.get().getUserStatus().equals(UserStatus.Inactive.getValue())) && userDetailsTryHarderEnabled) {

            log.warn("Credentials were empty from the database, fetching from UNITE");
            userCredentials = getUserDetailsFromOnPremAccountService(usernameBrandArray[0], usernameBrandArray[1]);
        }

        userCredentials.orElseThrow(() -> new UsernameNotFoundException("User not found for username: " + username));

        UserAuthDetails userAuthDetails = userCredentials.get();

        if (Boolean.TRUE.equals(userAuthDetails.getCredentialLocked())) {
            throw new LockedException("Account locked.");
        }

        return new CustomUserDetails(userAuthDetails);
    }

    public String loadUsernameByBrand(String branding, Map<String, String> lookupFieldValuesMap) throws UniqueUsernameNotfoundException, InvalidBrandingException, ConflictUsernameLookupSearchException {

        var externalSource = externalSourceService.findBySourceCode(branding);

        if (externalSource.isEmpty() || externalSource.get().getExternalType().searchSchema() == null) {
            throw new InvalidBrandingException("Brand %s does not have an external source".formatted(branding));
        }

        Map<String, String> modifiedParams = transformKeys(lookupFieldValuesMap);

        var response = onPremAccountServiceClient.lookupUsername(externalSource.get().getSourceCode(), externalSource.get().getExternalType().searchSchema(), modifiedParams);

        if (response.isEmpty()) {
            throw new UniqueUsernameNotfoundException("Username not found in OnPrem Account Service: [source: %s]".formatted(externalSource.get().getSourceCode()));
        } else if (response.get() == CreateUserProfileDto.CONFLICT_USERNAME_LOOKUP) {
            throw new ConflictUsernameLookupSearchException("Username lookup resulted in more than one record");
        }

        return response.get().getCredential().getUsername();
    }

    public boolean getUserApprovalStatus(String branding, String username) throws InvalidBrandingException {

        var externalSource = externalSourceService.findBySourceCode(branding);

        if (externalSource.isEmpty()) {
            throw new InvalidBrandingException("Branding %s was not found".formatted(branding));
        }

        if (externalSource.get().getExternalType().name().equalsIgnoreCase("ABLE")) {

            Optional<UserApprovalStatusDto> userApprovalStatusDto = onPremAccountServiceClient.getUserApprovalStatus(username, externalSource.get().getSourceCode(), externalSource.get().getExternalType().searchSchema());

            if (userApprovalStatusDto.isPresent()) {
                return userApprovalStatusDto.get().isApprovalStatus();
            } else {
                log.warn("UserApproval status was not found for user {}", username);
            }

        } else {
            log.info("Branding %s is not an ABLE branding. Skipping Approval Status check {}", branding);
        }

        return true;
    }

    private Optional<UserAuthDetails> getUserDetailsFromOnPremAccountService(String username, String branding) {

        Optional<UserAuthDetails> userAuthDetails = Optional.empty();

        try {
            var externalSource = externalSourceService.findBySourceCode(branding);

            if (externalSource.isEmpty()) {
                throw new RuntimeException("Brand %s does not have an external source".formatted(branding));
            }

            var response = onPremAccountServiceClient.getUserAccount(username, externalSource.get().getSourceCode(), externalSource.get().getExternalType().searchSchema());

            log.info("Response from OnPremAccountService: {}", response);

            if (response.isEmpty()) {
                throw new RuntimeException("User not found in OnPrem Account Service: [username: %s, source: %s]".formatted(username, externalSource.get().getSourceCode()));
            }

            var userToCreate = UserDtoMapper.Instance.toCreateUserDto(response.get());

            var org = organizationService.get(userToCreate.getOrgGuid()).orElseThrow(() -> new OrgNotFoundException("Organization %s not found".formatted(userToCreate.getOrgGuid())));

            userToCreate.setOrgId(org.getId());

            var sourceHeaders = getSourceHeaders();

            CreateUserNotificationConfig notificationConfig = new CreateUserNotificationConfig();
            notificationConfig.setFailNotificationSilently(true);

            userService.createOrReactivateUser(response.get(), userToCreate, sourceHeaders, externalSource.get(), notificationConfig);

            userAuthDetails = userService.getUserAuthDetailsByUsernameAndExternalSourceCode(username, externalSource.get().getSourceCode());

        } catch (UserCreationFailedException e) {
            log.error("Failed to create a user with information from OnPremAccountService", e);
        } catch (InvalidBrandingException e) {
            log.error("Failed to create user because of invalid brand", e);
        } catch (RuntimeException e) {
            log.error("Something went wrong while getting details from OnPremAccountService", e);
        } catch (OrgNotFoundException | UserSyncFailedException e) {
            log.error(e.getMessage(), e);
        }

        return userAuthDetails;
    }

    @NotNull
    Map<String, String> getSourceHeaders() {

        var savedRequest = requestCache.getRequest(authRequest, authResponse);

        var requestDate = savedRequest.getParameterValues(ApplicationConstants.REQUEST_DATETIME_HEADER);

        return Map.of(ApplicationConstants.REQUEST_ID_HEADER, UUID.randomUUID().toString(), ApplicationConstants.REQUEST_DATETIME_HEADER, requestDate[0]);
    }

    private Map<String, String> transformKeys(Map<String, String> originalMap) {

        Map<String, String> transformedMap = new HashMap<>();

        for (Map.Entry<String, String> entry : originalMap.entrySet()) {

            String oldKey = entry.getKey();
            String newKey = oldKey;

            if (oldKey.toLowerCase().contains("email")) {
                newKey = "email";
            } else if (oldKey.toLowerCase().contains("ssn")) {
                newKey = "last4SSN";
            } else if (oldKey.toLowerCase().contains("number")) {
                newKey = "accountNumber";
            }

            transformedMap.put(newKey, entry.getValue());
        }

        return transformedMap;
    }
}

