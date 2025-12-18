package com.chellavignesh.authserver.adminportal.user;

import com.chellavignesh.authserver.adminportal.externalsource.ExternalSourceService;
import com.chellavignesh.authserver.adminportal.externalsource.entity.ExternalSource;
import com.chellavignesh.authserver.adminportal.externalsource.exception.InvalidBrandingException;
import com.chellavignesh.authserver.adminportal.globalconfig.GlobalConfigCache;
import com.chellavignesh.authserver.adminportal.metadata.dto.OutgoingMetadataDto;
import com.chellavignesh.authserver.adminportal.organization.entity.OrganizationGroupPermission;
import com.chellavignesh.authserver.adminportal.organization.entity.PermissionsMap;
import com.chellavignesh.authserver.adminportal.toggle.AccountSyncType;
import com.chellavignesh.authserver.adminportal.user.dto.*;
import com.chellavignesh.authserver.adminportal.user.entity.*;
import com.chellavignesh.authserver.adminportal.user.exception.*;
import com.chellavignesh.authserver.adminportal.user.mapper.UserDtoMapper;
import com.chellavignesh.authserver.adminportal.util.map.diff.MapDiffUtil;
import com.chellavignesh.authserver.adminportal.util.map.diff.PropertyExtractor;
import com.chellavignesh.authserver.enums.entity.GlobalConfigTypeEnum;
import com.chellavignesh.authserver.notification.NotificationContext;
import com.chellavignesh.authserver.notification.NotificationService;
import com.chellavignesh.authserver.session.LibCryptoPasswordEncoder;
import com.chellavignesh.authserver.session.PasswordEncoderFactory;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

@Service
@Slf4j
public class UserService {
    private static final List<PropertyExtractor<UserDetails, UpdateUserDto>> UPDATE_PROFILE_PROPERTY_EXTRACTORS = List.of(new PropertyExtractor<>("firstName", UserDetails::firstName, UpdateUserDto::getFirstName), new PropertyExtractor<>("lastName", UserDetails::lastName, UpdateUserDto::getLastName), new PropertyExtractor<>("phoneNumber", UserDetails::phoneNumber, UpdateUserDto::getPhoneNumber), new PropertyExtractor<>("secondaryPhoneNumber", UserDetails::secondaryPhoneNumber, UpdateUserDto::getSecondaryPhoneNumber));

    private static final List<PropertyExtractor<UserDetails, ChangeProfileDto>> UPDATE_FOR_CHANGE_PROFILE_PAGE_PROPERTY_EXTRACTORS = List.of(new PropertyExtractor<>("email", UserDetails::email, ChangeProfileDto::getEmail), new PropertyExtractor<>("phoneNumber", UserDetails::phoneNumber, ChangeProfileDto::getPhoneNumber), new PropertyExtractor<>("secondaryPhoneNumber", UserDetails::secondaryPhoneNumber, ChangeProfileDto::getSecondaryPhoneNumber));

    private final UserRepository userRepository;
    private final ExternalSourceService externalSourceService;
    private final PasswordEncoderFactory passwordEncoderFactory;
    private final NotificationService notificationService;
    private final GlobalConfigCache globalConfigCache;
    private final OnPremAccountServiceClient onPremAccountService;
    private final AccountSyncType accountSyncType;

    @Autowired
    public UserService(UserRepository userRepository, ExternalSourceService externalSourceService, PasswordEncoderFactory passwordEncoderFactory, NotificationService notificationService, GlobalConfigCache globalConfigCache, OnPremAccountServiceClient onPremAccountService, @Value("${toggles.account.sync.type:none}") AccountSyncType accountSyncType) {
        this.userRepository = userRepository;
        this.externalSourceService = externalSourceService;
        this.passwordEncoderFactory = passwordEncoderFactory;
        this.notificationService = notificationService;
        this.globalConfigCache = globalConfigCache;
        this.onPremAccountService = onPremAccountService;
        this.accountSyncType = accountSyncType;
    }

    public User create(CreateUserDto dto, int groupId, boolean hashedPassword) throws UserCreationFailedException, InvalidBrandingException {
        return create(dto, groupId, hashedPassword, "", Map.of(), CreateUserNotificationConfig.getDefault());
    }

    public User create(CreateUserDto dto, int groupId, boolean hashedPassword, @NotNull String notificationIntent, Map<String, String> headers, CreateUserNotificationConfig notificationConfig) throws UserCreationFailedException, InvalidBrandingException {

        log.info("Creating user with branding: {} and intent: {} and syncFlag: {}", dto.getBranding(), notificationIntent, dto.getSyncFlag());

        final var branding = dto.getBranding();
        if (branding == null) {
            log.info("Branding was not included with the create user request.");
            throw new InvalidBrandingException("Failed to create user. Branding was not included.");
        }

        // TODO: implement reading external source from caching service, cacheable annotation
        final var externalSource = externalSourceService.findBySourceCode(branding)
                .orElseThrow(() -> new InvalidBrandingException("Failed to create user. Invalid Branding: " + branding + "."));
        final var externalId = externalSource.getSourceId();

        log.info("Successfully found external source: {}", externalId);

        validateIfUserExists(dto, branding, externalSource, notificationIntent, headers, notificationConfig);

        if (dto.getSyncFlag() == null) {
            log.info("Sync flag was not included with the create user request.");
            dto.setSyncFlag(externalSource.isSyncFlag());
        }

        User user;
        Integer disallowedRecentPasswordCount = globalConfigCache.getGlobalConfig(GlobalConfigTypeEnum.DISALLOWED_RECENT_PASSWORD_COUNT);

        if (hashedPassword) {
            log.info("User already had a hashed password");
            user = userRepository.create(dto, dto.getPassword(), 0, groupId, externalId, disallowedRecentPasswordCount);
        } else {
            // TODO: This base64 encode is going to be a problem until the WAF rule allows all characters in a password.
            log.info("User did not already have a hashed password");
            user = userRepository.create(dto, passwordEncoderFactory.encode(Base64.getEncoder().encodeToString(dto.getPassword().getBytes())), PasswordEncoderFactory.currentVersion, groupId, externalId, disallowedRecentPasswordCount);
        }

        if (user == null) {
            log.info("Failed to create new user with branding: {}", dto.getBranding());
            throw new UserCreationFailedException("Failed to create new user.");
        }

        notifyProfileSync(user, externalSource, notificationIntent, headers, notificationConfig);

        return user;
    }

    public User createOrReactivateUser(CreateUserProfileDto createUserProfileDto, CreateUserDto createUserDto, Map<String, String> sourceHeaders, ExternalSource externalSource, CreateUserNotificationConfig notificationConfig) throws UserSyncFailedException, InvalidBrandingException, UserCreationFailedException {

        var includeIdpSyncDttm = !Objects.requireNonNullElse(createUserProfileDto.getProfile().getIdpSyncDttm(), "").isEmpty();
        var deletedUserOptional = getByGuidAndBranding(createUserDto.getMemberId(), externalSource.getSourceCode());

        if (createUserProfileDto.getIntent().equalsIgnoreCase("conversion")) {
            createUserDto.setSyncFlag(externalSource.isSyncFlag());
        }

        if ((includeIdpSyncDttm && deletedUserOptional.isPresent()) && deletedUserOptional.get().status() == UserStatus.Inactive) {
            log.info("Inactive user found in idp database. Attempting to reactivate.");
            return reactivateAccountWithPasswordAndNotify(createUserProfileDto, deletedUserOptional.get().rowGuid(), externalSource, LibCryptoPasswordEncoder.ENCODER_ID, 1, notificationConfig, createUserProfileDto.getIntent(), sourceHeaders);
        } else if (!includeIdpSyncDttm && deletedUserOptional.isPresent() && deletedUserOptional.get().status() == UserStatus.Inactive) {
            log.error("IdpSyncDto intent is not null & deleted user found in idp database. Could not complete request.");
            throw new UserSyncFailedException("Error with profile creation.");
        } else {
            // Create new user
            return create(createUserDto, 0, true, createUserProfileDto.getIntent(), sourceHeaders, notificationConfig);
        }
    }

    private void validateIfUserExists(CreateUserDto dto, final String branding, @NotNull ExternalSource externalSource, String notificationIntent, Map<String, String> headers, CreateUserNotificationConfig notificationConfig) {

        log.info("Validating if user exists with branding: {}", branding);
        User user = null;

        try {
            var optionalUser = userRepository.getUserByUsernameAndBranding(dto.getUsername(), branding);
            if (optionalUser.isPresent()) {
                log.info("User already exists with provided credentials for branding: {}", branding);
                user = optionalUser.get();
                throw new DataIntegrityViolationException("User with this username already exists.");
            }

            if (dto.getMemberId() != null) {
                Optional<UserDetails> userDetails = userRepository.getByGuid(dto.getMemberId());
                if (userDetails.isPresent()) {
                    user = userRepository.getById(userDetails.get().id()).get();
                    throw new DataIntegrityViolationException("User with this member id already exists.");
                }
            }

            if (dto.getLoginId() != null) {
                Optional<UserCredentials> userCredentials = userRepository.getByLoginId(dto.getLoginId());
                if (userCredentials.isPresent()) {
                    user = userRepository.getById(userCredentials.get().getProfileId()).get();
                    throw new DataIntegrityViolationException("User Credential with this login id already exists.");
                }
            }
        } catch (DataIntegrityViolationException ex) {
            log.error("User already exists, DataIntegrityViolationException: " + ex.getMessage(), ex);
            if (user != null && notificationConfig.isAlwaysSendNotification()) {
                notifyProfileSync(user, externalSource, notificationIntent, headers, notificationConfig);
            }
            throw ex;
        }
    }

    private void notifyProfileSync(@NotNull final User user, @NotNull final ExternalSource branding, @NotNull final String intent, @NotNull final Map<String, String> sourceHeaders, CreateUserNotificationConfig notificationConfig) {

        if (StringUtils.isEmpty(intent)) {
            return;
        }

        try {
            log.info("Sending sync notification to queue for user");
            final var timestamp = ZonedDateTime.now(ZoneOffset.UTC);
            notificationService.send(NotificationContext.createFor(intent, branding, user, sourceHeaders), (channel, context) -> channel.notifyProfileCreated(context, timestamp));
        } catch (Exception ex) {
            if (notificationConfig.isFailNotificationSilently()) {
                log.warn("Error while sending out sync notification: {}", ex.getMessage(), ex);
            } else {
                log.error("Error while sending out sync notification: {}", ex.getMessage(), ex);
                throw ex;
            }
        }
    }

    public User reactivateAccountWithPasswordAndNotify(CreateUserProfileDto dto, UUID userGuid, ExternalSource externalSource, int passwordVersion, int unlockAccount, CreateUserNotificationConfig notificationConfig, @NotNull String notificationIntent, Map<String, String> sourceHeaders) throws UserSyncFailedException {

        String password = UserDtoMapper.assemblePassword(dto.getCredential().getPasswordHash(), dto.getCredential().getcIndex());
        dto.getCredential().setPasswordHash(password);
        User user = userRepository.reactivateAccountWithPassword(dto, userGuid, externalSource.getSourceId(), passwordVersion, unlockAccount);

        notifyProfileSync(user, externalSource, notificationIntent, sourceHeaders, notificationConfig);

        return user;
    }

    public User update(UpdateUserDto dto, UUID userGuid, OutgoingMetadataDto metadata, Map<String, String> headers) throws UserUpdateFailedException, UserNotFoundException, AccountSyncException, UserSyncFailedException {

        log.info("update() - Updating user profile for user with GUID: {}", userGuid);
        final var userBeforeUpdate = userRepository.getByGuidAndBranding(userGuid, dto.getBranding())
                .orElseThrow(() -> new UserNotFoundException("User with GUID " + userGuid + " not found for the branding"));

        final var externalSource = resolveBrandingFrom(dto);
        UserDetails userDetails = getByGuidAndBranding(userGuid, dto.getBranding())
                .orElseThrow(() -> new UserNotFoundException("User with GUID " + userGuid + " not found for the branding"));

        if (dto.getMemberId() != null && userRepository.getByGuid(dto.getMemberId()).isPresent() && userGuid.compareTo(dto.getMemberId()) != 0) {
            throw new DataIntegrityViolationException("User with this member id already exists.");
        }

        if (dto.getLoginId() != null && userRepository.getByLoginId(dto.getLoginId()).isPresent()) {
            throw new DataIntegrityViolationException("User Credential with this login id already exists.");
        }

        User user;

        log.info("update() - Check for condition to process sync and intent, Properties sync flag is a value that is not sync or async: {} for user: {}", accountSyncType, userGuid);

        if (userDetails.profileSyncFlag() && AccountSyncType.ASYNC == accountSyncType && ("idp".equals(dto.getIntent().getId()))) {
            log.info("Updating user profile in IDP database only for user with GUID: {}", userGuid);
            ChangeProfileDto changeProfileDto = ChangeProfileDto.fromUpdateUserDto(dto);
            var response = this.onPremAccountService.syncProfile(changeProfileDto, metadata, userBeforeUpdate, externalSource.getSourceCode(), UUID.randomUUID().toString());
            if (response.statusCode() < 200 || response.statusCode() > 300) {
                log.error("Received a non-200 response from On-prem Account service while trying to sync profile: {} _body: {}", response.statusCode(), response.body());
                throw new UserSyncFailedException("User profile sync failed:");
            }

            user = new User();
            user.setFirstName(dto.getFirstName());
            user.setLastName(dto.getLastName());
            user.setLoginId(dto.getLoginId());
            user.setRowGuid(dto.getMemberId());
        }

        if (userDetails.profileSyncFlag() && AccountSyncType.ASYNC == accountSyncType) {
            log.info("Updating user profile in IDP database only for user with GUID: {}", userGuid);
            user = userRepository.update(dto, userGuid);
            if (user == null) {
                throw new UserUpdateFailedException("Failed to update user profile.");
            }

            final var userChangesForNotification = MapDiffUtil.createDiffFor(userBeforeUpdate, dto, UPDATE_PROFILE_PROPERTY_EXTRACTORS);

            if (!userChangesForNotification.isEmpty()) {
                notificationService.send(NotificationContext.createFor(dto, externalSource, user, headers),
                        (channel, context) -> channel.notifyProfileUpdate(context, userChangesForNotification));
            }
        } else { // Update user, no notification
            log.warn("update() - Properties sync flag is a value that is not sync or async from else block: {} + accountSyncType + for user: {} ", accountSyncType, userGuid);
            user = userRepository.update(dto, userGuid);
            if (user == null) {
                throw new UserUpdateFailedException("Failed to update user profile.");
            }
        }

        return user;
    }

    @NotNull
    public User updateForChangeProfilePage(ChangeProfileDto dto, UUID userGuid) throws UserUpdateFailedException, UserNotFoundException {

        log.info("Updating user profile for user with GUID: {}", userGuid);
        final var user = userRepository.updateForChangeProfilePage(dto, userGuid);

        if (user == null) {
            log.error("Failed to update user profile for user with GUID: {}", userGuid);
            throw new UserUpdateFailedException("Failed to update user profile.");
        }

        return user;
    }

    @NotNull
    public User updateForChangeProfilePageAndNotify(@NotNull ChangeProfileDto dto, @NotNull UUID userGuid, @NotNull String branding, @NotNull final OutgoingMetadataDto metadata, @NotNull Map<String, String> sourceHeaders) throws UserUpdateFailedException, UserNotFoundException {

        final var userBeforeUpdate = userRepository.getByGuid(userGuid).orElseThrow(() -> new UserNotFoundException("Such user not exists: " + userGuid));

        final var externalSource = resolveBrandingFrom(branding);
        final var user = updateForChangeProfilePage(dto, userGuid);
        final var userChangesForNotification = MapDiffUtil.createDiffFor(userBeforeUpdate, dto, UPDATE_FOR_CHANGE_PROFILE_PAGE_PROPERTY_EXTRACTORS);

        log.info("User changes for notification: {}", userChangesForNotification);

        if (!userChangesForNotification.isEmpty()) {
            final var notificationContext = NotificationContext.createFor(UpdateIntent.IDP.getId(), externalSource, user, metadata, sourceHeaders);
            notificationService.send(notificationContext, (channel, context) -> channel.notifyProfileUpdate(context, userChangesForNotification));
        }

        return user;
    }

    public User updateEmail(UpdateUserEmailDto dto, UserDetails userDetails, OutgoingMetadataDto metadata, Map<String, String> sourceHeaders) throws UserUpdateFailedException, AccountSyncException, UserSyncFailedException {

        final var branding = resolveBrandingFrom(dto);
        User user = null;

        log.info("updateEmail() - check intent: {}", dto.getIntent().getId());

        if (userDetails.profileSyncFlag() && AccountSyncType.SYNC == accountSyncType && ("idp".equals(dto.getIntent().getId()))) {

            log.info("Updating email in both Unite and IDP databases:");

            ChangeProfileDto changeProfileDto = new ChangeProfileDto();
            changeProfileDto.setEmail(dto.getEmail());
            var response = this.onPremAccountService.syncProfile(changeProfileDto, metadata, userDetails, branding.getSourceCode(), UUID.randomUUID().toString());

            if (response.statusCode() < 200 || response.statusCode() > 300) {
                log.error("Received a non-200 response from On-prem Account service while trying to sync profile: {} _body: {}", response.statusCode(), response.body());
                throw new UserSyncFailedException("User email sync failed:");
            }
        }

        if (userDetails.profileSyncFlag() && AccountSyncType.ASYNC == accountSyncType) {
            log.info("Updating email in IDP database only:");
            user = this.updateEmailAndNotify(dto, userDetails.rowGuid(), branding, sourceHeaders);
        } else {
            log.warn("updateEmail() - Properties sync flag is a value that is not sync or async: {}", accountSyncType);
            user = userRepository.updateEmail(dto, userDetails.rowGuid());
        }

        return user;
    }

    public User updateEmailAndNotify(UpdateUserEmailDto dto, UUID userGuid, ExternalSource branding, Map<String, String> sourceHeaders) throws UserUpdateFailedException {

        User user = userRepository.updateEmail(dto, userGuid);
        if (user == null) {
            throw new UserUpdateFailedException("Failed to update user email.");
        }

        notificationService.send(NotificationContext.createFor(dto, branding, user, sourceHeaders), (channel, context) -> channel.notifyEmailChange(context, dto.getEmail()));

        return user;
    }

    public User updatePassword(UpdateUserPasswordDto dto, UUID userGuid) throws UserUpdateFailedException, InvalidBrandingException {
        return this.updatePassword(dto, userGuid, false);
    }

    public User updatePassword(UpdateUserPasswordDto dto, UUID userGuid, boolean unlockAccount) throws UserUpdateFailedException, InvalidBrandingException {

        String branding = dto.getBranding();
        var externalSource = Optional.<ExternalSource>empty();

        if (StringUtils.isBlank(branding)) {
            log.error("Branding was not included with the update password request.");
            throw new InvalidBrandingException("Failed to update user password. Branding was not included.");
        }

        externalSource = externalSourceService.findBySourceCode(branding);
        if (externalSource.isEmpty()) {
            log.error("Branding {} invalid.", branding);
            throw new InvalidBrandingException("Failed to update user password. Invalid branding: " + branding + ".");
        }

        User user = savePasswordWithExternalSource(dto, userGuid, externalSource.get(), unlockAccount);

        if (user == null) {
            throw new UserUpdateFailedException("Failed to update user password.");
        }
        return user;
    }

    @NotNull
    public User updatePasswordAndNotify(@NotNull final NotifiableUpdateUserPasswordDto dto, @NotNull final UUID userGuid, boolean unlockAccount, Map<String, String> sourceHeaders) throws UserUpdateFailedException {

        final var branding = resolveBrandingFrom(dto);
        final var user = savePasswordWithExternalSource(dto, userGuid, branding, unlockAccount);

        if (user == null) {
            throw new UserUpdateFailedException("Failed to update user password.");
        }

        notificationService.send(NotificationContext.createFor(dto, branding, user, sourceHeaders), (channel, context) -> channel.notifyPasswordChange(context, dto.getPassword()));

        return user;
    }

    public User updateUsername(UpdateUsernameDto dto, UUID userGuid, ExternalSource externalSource, Map<String, String> sourceHeaders) throws UserUpdateFailedException {

        User user = userRepository.updateUsername(dto, userGuid, externalSource.getSourceId());
        if (user == null) {
            throw new UserUpdateFailedException("Failed to update user password.");
        }

        notificationService.send(NotificationContext.createFor(dto, externalSource, user, sourceHeaders), (channel, context) -> channel.notifyUsernameChange(context, dto.getUsername()));

        return user;
    }

    private User savePasswordWithExternalSource(UpdateUserPasswordDto dto, UUID userGuid, ExternalSource externalSource, boolean unlockAccount) throws UserUpdateFailedException {

        UUID externalId;
        if (externalSource == null) {
            externalId = null;
        } else {
            externalId = externalSource.getSourceId();
        }

        Integer disallowedRecentPasswordCount = globalConfigCache.getGlobalConfig(GlobalConfigTypeEnum.DISALLOWED_RECENT_PASSWORD_COUNT);

        return userRepository.updatePassword(dto, userGuid, passwordEncoderFactory.encode(Base64.getEncoder().encodeToString(dto.getPassword().getBytes())), PasswordEncoderFactory.currentVersion, externalId, unlockAccount, disallowedRecentPasswordCount);
    }

    public User savePassword(UpdateUserPasswordDto dto, UUID userGuid, boolean unlockAccount) throws UserUpdateFailedException {
        Integer disallowedRecentPasswordCount = globalConfigCache.getGlobalConfig(GlobalConfigTypeEnum.DISALLOWED_RECENT_PASSWORD_COUNT);
        return userRepository.updatePassword(dto, userGuid, passwordEncoderFactory.encode(Base64.getEncoder().encodeToString(dto.getPassword().getBytes())), PasswordEncoderFactory.currentVersion, unlockAccount, disallowedRecentPasswordCount);
    }

    // fetches only Active users(Person.Profile.Status = 1)
    public Optional<UserDetails> getByUsername(String username) {
        Optional<User> user = userRepository.getUserByUsername(username);
        return this.getByGuid(user.get().getRowGuid());
    }

    public Optional<UserDetails> getByUsernameAndBranding(String username, String branding) {
        final var user = userRepository.getUserByUsernameAndBranding(username, branding);
        return user.map(u -> this.getByGuidAndBranding(user.get().getRowGuid(), branding)).orElse(Optional.empty());
    }

    public Optional<UserCredentials> getCredentialsByUsername(String username) {
        return userRepository.getCredentialsByUsername(username);
    }

    public Optional<UserCredentialsWithBranding> getCredentialsByUsernameAndBranding(String username, String branding) {
        return userRepository.getCredentialsByUsernameAndBranding(username, branding);
    }

    public User updateSecuritySettings(UpdateUserSecuritySettingsDto dto, UUID userGuid) throws UserUpdateFailedException {

        User user = userRepository.updateSecuritySettings(dto, userGuid);

        if (user == null) {
            throw new UserUpdateFailedException("Failed to update 2 factor enabled.");
        }
        return user;
    }

    public User updateMetadata(UpdateUserMetadataDto dto, UUID userGuid) throws UserUpdateFailedException, UserNotFoundException {

        User user = userRepository.updateMetadata(dto, userGuid);

        if (user == null) {
            throw new UserUpdateFailedException("Failed to update user metadata.");
        }
        return user;
    }

    public Map<String, String> getMetadata(UUID userGuid) throws UserNotFoundException {
        HashMap<String, String> metadata = new HashMap<>();

        for (UserMetadata userMetadata : userRepository.getMetadata(userGuid)) {
            metadata.put(userMetadata.key(), userMetadata.value());
        }
        return metadata;
    }

    public User deleteByGuid(UUID userGuid) throws UserDeleteFailedException {
        User user = userRepository.updateStatus(0, userGuid);
        if (user == null) {
            throw new UserDeleteFailedException("Failed to inactive user.");
        }
        return user;
    }

    public Optional<User> updateUserStatusByGuid(UserStatus status, UUID userGuid) throws UserDeleteFailedException {

        return Optional.ofNullable(userRepository.updateStatus(status.getValue(), userGuid));
    }

    public Optional<UserDetails> getByGuid(UUID userGuid) {
        return userRepository.getByGuid(userGuid);
    }

    public Optional<UserDetails> getByGuidAndBranding(@Valid UUID userGuid, String branding) {
        log.info("Getting user by GUID: {} and branding: {}", userGuid, branding);
        return userRepository.getByGuidAndBranding(userGuid, branding);
    }

    public List<UserDetails> getAll(Optional<Integer> resultsPerPage, Optional<Integer> offset, Optional<String> type, Optional<String> search, Optional<UUID> organizationId, Optional<Integer> status, List<String> brandingSourceCodes) {

        final var notEmptyDistinctSourceCodes = brandingSourceCodes.stream().filter(StringUtils::isNotBlank).distinct().toList();

        final var resolvedExternalSources = externalSourceService.findAllBySourceCodes(notEmptyDistinctSourceCodes);

        if (notEmptyDistinctSourceCodes.size() != resolvedExternalSources.size()) {
            log.error("There was a miss-match resolving branding sourceCodes in database: {}, {}", notEmptyDistinctSourceCodes, resolvedExternalSources);
        }

        final var sourceIds = resolvedExternalSources.stream().map(ExternalSource::getSourceId).toList();

        return userRepository.getAll(resultsPerPage, offset, type, search, organizationId, status, sourceIds);
    }

    public Optional<UserAuthDetails> getUserAuthDetailsByUserName(String username) {
        return this.userRepository.getUserAuthDetailsByUserName(username);
    }

    public Optional<UserAuthDetails> getUserAuthDetailsByUsernameAndExternalSourceCode(String username, String externalSourceCode) {
        return this.userRepository.getUserAuthDetailsByUsernameAndExternalSourceCode(username, externalSourceCode);
    }

    public Map<String, List<String>> getUserPermissions(Integer userId) {
        List<OrganizationGroupPermission> groupPermissionList = this.userRepository.getUserPermissions(userId);
        return PermissionsMap.getPermissionsMap(groupPermissionList);
    }

    public Optional<UserAuthDetails> updateAccessFailedCountWithExternalSourceCode(String userName, String sourceCode, int loginSuccess) {

        int accessFailedLimit = this.globalConfigCache.getGlobalConfig(GlobalConfigTypeEnum.MAX_PASSWORD_FAILURE_COUNT);
        return this.userRepository.updateAccessFailedCountWithExternalSourceCode(userName, sourceCode, loginSuccess, accessFailedLimit);
    }

    public void unLockAccount(String userName, String sourceCode) {
        this.userRepository.unlockAccount(userName, sourceCode);
    }

    public void lockAccount(String userName, String sourceCode) {
        int accessFailedLimit = this.globalConfigCache.getGlobalConfig(GlobalConfigTypeEnum.MAX_PASSWORD_FAILURE_COUNT);
        this.userRepository.lockAccount(userName, sourceCode, accessFailedLimit);
    }

    public boolean isUserAccountLocked(UserAuthDetails userAuthDetails) {
        return userAuthDetails.getCredentialLocked();
    }

    @NotNull
    private ExternalSource resolveBrandingFrom(@NotNull final NotifiableUpdateDto dto) throws UserUpdateFailedException {
        return resolveBrandingFrom(dto.getBranding());
    }

    @NotNull
    private <T> ExternalSource resolveBrandingFrom(@NotNull final String branding) throws UserUpdateFailedException {

        final var externalSource = externalSourceService.findBySourceCode(branding);

        if (externalSource.isEmpty()) {
            throw new UserUpdateFailedException("Cannot resolve branding for notifications, cannot continue!");
        }

        return externalSource.get();
    }
}
