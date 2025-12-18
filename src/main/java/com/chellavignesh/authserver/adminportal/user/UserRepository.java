package com.chellavignesh.authserver.adminportal.user;

import com.chellavignesh.authserver.adminportal.organization.entity.OrganizationGroupPermission;
import com.chellavignesh.authserver.adminportal.organization.entity.OrganizationGroupPermissionRowMapper;
import com.chellavignesh.authserver.adminportal.user.dto.*;
import com.chellavignesh.authserver.adminportal.user.entity.*;
import com.chellavignesh.authserver.adminportal.user.exception.*;
import com.chellavignesh.authserver.adminportal.util.MapSqlParameterBuilder;
import com.chellavignesh.authserver.adminportal.util.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Slf4j
public class UserRepository {

    @NotNull
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @NotNull
    private final SecurityUtil securityUtil;

    @Autowired
    public UserRepository(@NotNull NamedParameterJdbcTemplate namedParameterJdbcTemplate, @NotNull SecurityUtil securityUtil) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.securityUtil = securityUtil;
    }

    public Optional<Profile> getProfile(UUID profileGuid) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("profileGuid", profileGuid);

        return namedParameterJdbcTemplate.query("{call Person.GetProfile(:profileGuid)}", parameters, new ProfileRowMapper()).stream().findFirst();
    }

    public User create(CreateUserDto dto, String hashPassword, Integer version, int groupId, UUID externalId, int disallowedRecentPasswordCount) throws UserCreationFailedException {

        log.debug("Creating user in the database with syncFlag: {}", dto.getSyncFlag());

        var parameters = new MapSqlParameterSource()
                .addValue("orgId", dto.getOrgId())
                .addValue("firstName", dto.getFirstName())
                .addValue("lastName", dto.getLastName())
                .addValue("username", dto.getUsername())
                .addValue("email", dto.getEmail())
                .addValue("phoneNumber", dto.getPhoneNumber())
                .addValue("password", hashPassword, Types.VARBINARY)
                .addValue("version", version)
                .addValue("groupId", groupId)
                .addValue("memberId", dto.getMemberId())
                .addValue("loginId", dto.getLoginId())
                .addValue("externalId", externalId)
                .addValue("secondaryPhoneNumber", dto.getSecondaryPhoneNumber())
                .addValue("disallowedRecentPasswordCount", disallowedRecentPasswordCount)
                .addValue("syncFlag", dto.getSyncFlag());

        log.debug("Creating user in the database with parameters: {}", parameters);

        Optional<Integer> appId = namedParameterJdbcTemplate.query(
                "{call Person.CreateUser(" + ":orgId," + ":firstName," + ":lastName," + ":username," + ":password," + ":version," + ":groupId," + ":email," + ":phoneNumber," + ":memberId," + ":loginId," + ":externalId," + ":secondaryPhoneNumber," + ":disallowedRecentPasswordCount," + ":syncFlag" + ")}",
                parameters,
                (rs, rowNum) -> rs.getInt("ID")).stream().findFirst();

        if (appId.isPresent()) {
            log.debug("User created in the database with appId: {}", appId.get());

            if (dto.getMetaData() != null) {
                for (String key : dto.getMetaData().keySet()) {
                    log.trace("Creating metadata for appId: {} key: {}", appId.get(), key);

                    parameters = new MapSqlParameterSource().addValue("profileId", appId.get()).addValue("key", key).addValue("value", dto.getMetaData().get(key));

                    namedParameterJdbcTemplate.update("{call Person.CreateMetadata(:profileId, :key, :value)}", parameters);
                }
            }

            return getById(appId.get()).get();
        } else {
            log.debug("User creation failed in the database");
            throw new UserCreationFailedException("Could not fetch newly created user by ID.");
        }
    }

    public User update(UpdateUserDto dto, UUID userGuid) throws UserUpdateFailedException, UserNotFoundException {

        Optional<UserDetails> userDetails = getByGuid(userGuid);

        if (userDetails.isEmpty()) {
            throw new UserNotFoundException("User does not exist.");
        }

        var parameters = new MapSqlParameterSource().addValue("userGuid", userGuid).addValue("firstName", dto.getFirstName()).addValue("lastName", dto.getLastName()).addValue("title", dto.getTitle()).addValue("middleInitial", dto.getMiddleInitial()).addValue("phoneNumber", dto.getPhoneNumber()).addValue("suffix", dto.getSuffix() != null ? dto.getSuffix().getValue() : 0).addValue("memberId", dto.getMemberId()).addValue("loginId", dto.getLoginId()).addValue("modifiedOn", new Date()).addValue("modifiedBy", securityUtil.getTokenUserGuid()).addValue("secondaryPhoneNumber", dto.getSecondaryPhoneNumber());

        Optional<Integer> appId = namedParameterJdbcTemplate.query("{call Person.UpdateUserProfile(" + ":userGuid," + ":firstName," + ":lastName," + ":title," + ":middleInitial," + ":phoneNumber," + ":suffix," + ":memberId," + ":loginId," + ":modifiedOn," + ":modifiedBy," + ":secondaryPhoneNumber" + ")}",
                parameters,
                (rs, _) -> rs.getInt("ID")).stream().findFirst();

        if (appId.isPresent()) {
            return getById(appId.get()).get();
        } else {
            throw new UserUpdateFailedException("User does not exist.");
        }
    }

    public User updateForChangeProfilePage(ChangeProfileDto dto, UUID userGuid) throws UserUpdateFailedException, UserNotFoundException {

        Optional<UserDetails> userDetails = getByGuid(userGuid);

        if (userDetails.isEmpty()) {
            throw new UserNotFoundException("User does not exist.");
        }

        log.debug("Updating user in the database with userGuid: {}", userGuid);

        var parameters = new MapSqlParameterSource().addValue("userGuid", userGuid).addValue("email", dto.getEmail()).addValue("phoneNumber", dto.getPhoneNumber()).addValue("secondaryPhoneNumber", dto.getSecondaryPhoneNumber()).addValue("modifiedBy", securityUtil.getTokenUserGuid());

        log.debug("Updating user in the database with parameters: {}", parameters);

        Optional<Integer> appId = namedParameterJdbcTemplate.query("{call Person.UpdateUserForChangeProfilePage(" + ":userGuid," + ":email," + ":phoneNumber," + ":secondaryPhoneNumber," + ":modifiedBy" + ")}",
                parameters,
                (rs, _) -> rs.getInt("ID")).stream().findFirst();

        if (appId.isPresent()) {
            log.info("User updated in the database with appId: {}", appId.get());
            return getById(appId.get()).get();
        } else {
            log.info("User update failed in the database with userGuid:{}", userGuid);
            throw new UserUpdateFailedException("User does not exist.");
        }
    }

    public User updateEmail(UpdateUserEmailDto dto, UUID userGuid) throws UserUpdateFailedException {

        var parameters = new MapSqlParameterSource()
                .addValue("userGuid", userGuid)
                .addValue("email", dto.getEmail())
                .addValue("modifiedOn", new Date())
                .addValue("modifiedBy", securityUtil.getTokenUserGuid());

        Optional<Integer> appId = namedParameterJdbcTemplate.query(
                "{call Person.UpdateUserEmail(:userGuid, :email, :modifiedOn, :modifiedBy)}",
                parameters,
                (rs, _) -> rs.getInt("ID")
        ).stream().findFirst();

        if (appId.isPresent()) {
            return getById(appId.get()).get();
        } else {
            throw new UserUpdateFailedException("User does not exist.");
        }
    }

    // Overloaded method – calls v1 of Person.UpdateUserPassword
    public User updatePassword(UpdateUserPasswordDto dto, UUID userGuid, String hashPassword, Integer version, boolean unlockAccount, Integer disallowedRecentPasswordCount) throws UserUpdateFailedException {

        var parameters = new MapSqlParameterSource()
                .addValue("userGuid", userGuid)
                .addValue("password", hashPassword, Types.VARBINARY)
                .addValue("version", version)
                .addValue("modifiedOn", new Date())
                .addValue("modifiedBy", securityUtil.getTokenUserGuid())
                .addValue("unlockAccount", unlockAccount ? 1 : 0)
                .addValue("disallowedRecentPasswordCount", disallowedRecentPasswordCount);

        Optional<Integer> appId = namedParameterJdbcTemplate.query(
                "{call Person.UpdateUserPassword(:userGuid, :password, :version, :modifiedOn, :modifiedBy, :unlockAccount, :disallowedRecentPasswordCount)}",
                parameters,
                (rs, rowNum) -> rs.getInt("ID")
        ).stream().findFirst();

        if (appId.isPresent()) {
            return getById(appId.get()).get();
        } else {
            throw new UserUpdateFailedException("User does not exist.");
        }
    }

    // Overloaded method – calls v2 of Person.UpdateUserPassword (with externalId)
    public User updatePassword(UpdateUserPasswordDto dto, UUID userGuid, String hashPassword, Integer version, UUID externalId, boolean unlockAccount, Integer disallowedRecentPasswordCount) throws UserUpdateFailedException {

        var parameters = new MapSqlParameterSource()
                .addValue("userGuid", userGuid)
                .addValue("password", hashPassword, Types.VARBINARY)
                .addValue("version", version)
                .addValue("modifiedOn", new Date())
                .addValue("modifiedBy", securityUtil.getTokenUserGuid())
                .addValue("externalId", externalId)
                .addValue("unlockAccount", unlockAccount ? 1 : 0)
                .addValue("disallowedRecentPasswordCount", disallowedRecentPasswordCount);

        Optional<Integer> appId = namedParameterJdbcTemplate.query(
                "{call Person.UpdateUserPassword_V2(:userGuid, :password, :version, :modifiedOn, :modifiedBy, :externalId, :unlockAccount, :disallowedRecentPasswordCount)}",
                parameters,
                (rs, rowNum) -> rs.getInt("ID")
        ).stream().findFirst();

        if (appId.isPresent()) {
            return getById(appId.get()).get();
        } else {
            throw new UserUpdateFailedException("User does not exist.");
        }
    }

    public User updateUsername(UpdateUsernameDto dto, UUID userGuid, UUID externalId) throws UserUpdateFailedException {
        var parameters = new MapSqlParameterSource()
                .addValue("userGuid", userGuid)
                .addValue("username", dto.getUsername())
                .addValue("modifiedOn", new Date())
                .addValue("modifiedBy", securityUtil.getTokenUserGuid())
                .addValue("externalId", externalId);

        Optional<Integer> userId = namedParameterJdbcTemplate.query(
                "{call Person.UpdateUsername(:userGuid, :username, :externalId, :modifiedOn, :modifiedBy)}",
                parameters,
                (rs, rowNum) -> rs.getInt("ID")
        ).stream().findFirst();

        if (userId.isPresent()) {
            return getById(userId.get()).get();
        } else {
            throw new UserUpdateFailedException("User does not exist.");
        }
    }

    public User updateSecuritySettings(UpdateUserSecuritySettingsDto dto, UUID userGuid) throws UserUpdateFailedException {

        var parameters = new MapSqlParameterSource()
                .addValue("userGuid", userGuid)
                .addValue("twoFactorEnabled", dto.getTwoFactorEnabled() ? 1 : 0)
                .addValue("modifiedOn", new Date())
                .addValue("modifiedBy", securityUtil.getTokenUserGuid());

        Optional<Integer> appId = namedParameterJdbcTemplate.query(
                "{call Person.UpdateUserSecuritySettings(:userGuid, :twoFactorEnabled, :modifiedOn, :modifiedBy)}",
                parameters,
                (rs, rowNum) -> rs.getInt("ID")
        ).stream().findFirst();

        if (appId.isPresent()) {
            return getById(appId.get()).get();
        } else {
            throw new UserUpdateFailedException("User does not exist.");
        }
    }

    public User updateMetadata(UpdateUserMetadataDto dto, UUID userGuid) throws UserUpdateFailedException, UserNotFoundException {

        Optional<UserDetails> userDetails = getByGuid(userGuid);

        if (userDetails.isPresent()) {

            var parameters = new MapSqlParameterSource()
                    .addValue("profileId", userDetails.get().id());

            namedParameterJdbcTemplate.update(
                    "{call Person.DeleteMetadata(:profileId)}",
                    parameters
            );
            for (String key : dto.getMetaData().keySet()) {
                parameters = new MapSqlParameterSource()
                        .addValue("profileId", userDetails.get().id())
                        .addValue("key", key)
                        .addValue("value", dto.getMetaData().get(key))
                        .addValue("modifiedOn", new Date())
                        .addValue("modifiedBy", securityUtil.getTokenUserGuid());

                namedParameterJdbcTemplate.update(
                        "{call Person.CreateMetadata(:profileId, :key, :value, :modifiedOn, :modifiedBy)}",
                        parameters
                );
            }

            return getById(userDetails.get().id()).get();
        } else {
            throw new UserNotFoundException("User does not exist.");
        }
    }

    public List<UserMetadata> getMetadata(UUID userGuid) throws UserNotFoundException {

        Optional<UserDetails> userDetails = getByGuid(userGuid);

        if (userDetails.isPresent()) {

            var parameters = new MapSqlParameterSource()
                    .addValue("profileId", userDetails.get().id());

            return namedParameterJdbcTemplate.query(
                    "{call Person.GetMetadata(:profileId)}",
                    parameters,
                    new UserMetadataRowMapper()
            );
        } else {
            throw new UserNotFoundException("User does not exist.");
        }
    }

    public User updateStatus(int status, UUID userGuid) throws UserDeleteFailedException {

        var parameters = new MapSqlParameterSource()
                .addValue("userGuid", userGuid)
                .addValue("status", status)
                .addValue("modifiedOn", new Date())
                .addValue("modifiedBy", securityUtil.getTokenUserGuid());

        Optional<Integer> appId = namedParameterJdbcTemplate.query(
                "{call Person.UpdateUserStatus(:userGuid, :status, :modifiedOn, :modifiedBy)}",
                parameters,
                (rs, rowNum) -> rs.getInt("ID")
        ).stream().findFirst();

        if (appId.isPresent()) {
            return getById(appId.get()).get();
        } else {
            throw new UserDeleteFailedException("User does not exist.");
        }
    }

    public Optional<User> getById(Integer id) {
        log.info("Fetching user in the database by ID: {}", id);
        var parameters = new MapSqlParameterSource().addValue("id", id);
        return namedParameterJdbcTemplate.query(
                "{call Person.GetUserById(:id)}",
                parameters,
                new UserRowMapper()
        ).stream().findFirst();
    }

    public Optional<UserCredentials> getCredentialsByUsername(String username) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("username", username);

        return namedParameterJdbcTemplate.query(
                "{call Person.GetUserCredentialsByUsername(:username)}",
                parameters,
                new UserCredentialsRowMapper()
        ).stream().findFirst();
    }

    /**
     * @param username
     * @param branding
     * @return UserCredentialsWithBranding for given username and branding
     */
    public Optional<UserCredentialsWithBranding> getCredentialsByUsernameAndBranding(String username, String branding) {
        var parameters = new MapSqlParameterSource()
                .addValue("username", username)
                .addValue("branding", branding);

        return namedParameterJdbcTemplate.query(
                "{call Person.GetUserCredentialsByUsernameAndBranding(:username, :branding)}",
                parameters,
                new UserCredentialsWithBrandingRowMapper()
        ).stream().findFirst();
    }

    public Optional<UserCredentials> getByLoginId(UUID loginId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("loginId", loginId);

        return namedParameterJdbcTemplate.query(
                "{call Person.GetUserCredentialsByGuid(:loginId)}",
                parameters,
                new UserCredentialsRowMapper()
        ).stream().findFirst();
    }

    // Fetches only active users (Person.Profile.Status = 1)
    public Optional<User> getUserByUsername(String username) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("username", username);

        return namedParameterJdbcTemplate.query(
                "{call Person.GetUserByUsername(:username)}",
                parameters,
                new UserRowMapper()
        ).stream().findFirst();
    }

    public Optional<User> getUserByUsernameAndBranding(String username, String branding) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("username", username);
        parameters.addValue("branding", branding);

        return namedParameterJdbcTemplate.query(
                "{call Person.GetUserByUsernameAndBranding(:username, :branding)}",
                parameters,
                new UserRowMapper()
        ).stream().findFirst();
    }

    public Optional<UserDetails> getByGuid(UUID userGuid) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("userGuid", userGuid);

        return namedParameterJdbcTemplate.query(
                "{call Person.GetUserDetailsByUserId(:userGuid)}",
                parameters,
                new UserDetailsRowMapper()
        ).stream().findFirst();
    }

    public Optional<UserDetails> getByGuidAndBranding(UUID userGuid, String branding) {
        log.info("Fetching user in the database by userGuid: {} and branding: {}", userGuid, branding);

        var parameters = new MapSqlParameterSource()
                .addValue("userGuid", userGuid)
                .addValue("branding", branding);

        return namedParameterJdbcTemplate.query(
                "{call Person.GetUserDetailsByUserIdAndBrandingV2(:userGuid, :branding)}",
                parameters,
                new UserDetailsRowMapper()
        ).stream().findFirst();
    }

    public List<UserDetails> getAll(
            Optional<Integer> resultsPerPage,
            Optional<Integer> offset,
            Optional<String> type,
            Optional<String> search,
            Optional<UUID> organizationId,
            Optional<Integer> status,
            List<UUID> brandingSourcesIds
    ) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("resultsPerPage", resultsPerPage.get());
        parameters.addValue("offset", offset.get());
        parameters.addValue("status", status.orElse(null));
        parameters.addValue("search", search.orElse(null));
        parameters.addValue("type", type.orElse(null));
        parameters.addValue("organizationId", organizationId.orElse(null));
        parameters.addValue("sourceIds", brandingSourcesIds.isEmpty() ? null : StringUtils.join(brandingSourcesIds, ","));

        return namedParameterJdbcTemplate.query(
                "{call Person.GetUsers(:resultsPerPage, :offset, :status, :search, :type, :organizationId, :sourceIds)}",
                parameters,
                new UserDetailsRowMapper()
        );
    }

    /**
     * Retrieve essential user details required for authentication efficiently
     * with a single query. Intentionally avoids fetching unnecessary credentials.
     *
     * @param username
     * @return UserAuthDetails
     */
    public Optional<UserAuthDetails> getUserAuthDetailsByUserName(String username) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("username", username);

        return namedParameterJdbcTemplate.query(
                "{call Person.GetUserAuthDetailsByUserName(:username)}",
                parameters,
                new UserAuthDetailsRowMapper()
        ).stream().findFirst();
    }

    public Optional<UserAuthDetails> getUserAuthDetailsByUsernameAndExternalSourceCode(final String username, final String externalSourceCode) {
        return namedParameterJdbcTemplate.query(
                "{call Person.GetUserAuthDetailsByUserNameAndExternalSourceCode(:username, :sourceCode)}",
                MapSqlParameterBuilder.of(
                        "username", username,
                        "sourceCode", externalSourceCode
                ),
                new UserAuthDetailsRowMapper()
        ).stream().findFirst();
    }

    public Optional<ProfileOrganization> getProfileOrganizationByProfileId(final int profileId) {
        return namedParameterJdbcTemplate.query(
                "{call Person.GetProfileOrganizationByProfileId(:profileId)}",
                MapSqlParameterBuilder.of("profileId", profileId),
                ProfileOrganization.MAPPER
        ).stream().findFirst();
    }

    public List<OrganizationGroupPermission> getUserPermissions(Integer userId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("userId", userId);

        return namedParameterJdbcTemplate.query(
                "{call Person.GetUserPermissions(:userId)}",
                parameters,
                new OrganizationGroupPermissionRowMapper()
        );
    }

    public Optional<UserAuthDetails> updateAccessFailedCount(String username, int loginSuccess) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("username", username);
        parameters.addValue("loginSuccess", loginSuccess);

        return namedParameterJdbcTemplate.query(
                "{call Person.UpdateAccessFailedCount(:username, :loginSuccess)}",
                parameters,
                new UserAuthDetailsRowMapper()
        ).stream().findFirst();
    }

    public Optional<UserAuthDetails> updateAccessFailedCountWithExternalSourceCode(String username, String sourceCode, int loginSuccess, int accessFailedLimit) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("username", username);
        parameters.addValue("sourceCode", sourceCode);
        parameters.addValue("loginSuccess", loginSuccess);
        parameters.addValue("accessFailedLimit", accessFailedLimit);

        return namedParameterJdbcTemplate.query(
                "{call Person.UpdateAccessFailedCountWithExternalSourceCode(:username, :sourceCode, :loginSuccess, :accessFailedLimit)}",
                parameters,
                new UserAuthDetailsRowMapper()
        ).stream().findFirst();
    }


    public void unlockAccount(String username, String sourceCode) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("username", username);
        parameters.addValue("sourceCode", sourceCode);

        namedParameterJdbcTemplate.update(
                "{call Person.UnlockAccount(:username, :sourceCode)}",
                parameters
        );
    }

    public void lockAccount(String username, String sourceCode, int accessFailedLimit) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("username", username);
        parameters.addValue("sourceCode", sourceCode);
        parameters.addValue("accessFailedLimit", accessFailedLimit);

        namedParameterJdbcTemplate.update(
                "{call Person.LockAccount(:username, :sourceCode, :accessFailedLimit)}",
                parameters
        );
    }

    /**
     * The stored procedure [Person].[ReactivateUserWithPassword] should only be run for an inactive user
     * where the dto idpSyncDttm has a value.
     * If the user does not exist, or the status of the user is 1 (active user),
     * the user will not be updated.
     */
    public User reactivateAccountWithPassword(CreateUserProfileDto dto, UUID userGuid, UUID externalId, int passwordVersion, int unlockAccount) throws UserSyncFailedException {

        var parameters = new MapSqlParameterSource()
                .addValue("userGuid", userGuid)
                .addValue("firstName", dto.getProfile().getFirstName())
                .addValue("lastName", dto.getProfile().getLastName())
                .addValue("email", dto.getProfile().getEmail())
                .addValue("phoneNumber", dto.getProfile().getPhoneNumber())
                .addValue("memberGuid", dto.getProfile().getMemberId())
                .addValue("loginGuid", dto.getCredential().getLoginId())
                .addValue("secondaryPhoneNumber", dto.getProfile().getSecondaryPhoneNumber())
                .addValue("username", dto.getCredential().getUsername())
                .addValue("password", dto.getCredential().getPasswordHash(), Types.VARBINARY)
                .addValue("version", passwordVersion)
                .addValue("externalId", externalId)
                .addValue("unlockAccount", unlockAccount)
                .addValue("passwordAuditLimit", 12)
                .addValue("modifiedOn", new Date())
                .addValue("modifiedBy", securityUtil.getTokenUserGuid());

        Optional<Integer> userId = namedParameterJdbcTemplate.query(
                "{call Person.ReactivateUserWithPassword(" +
                        ":userGuid, " +
                        ":firstName, " +
                        ":lastName, " +
                        ":phoneNumber, " +
                        ":email, " +
                        ":memberGuid, " +
                        ":loginGuid, " +
                        ":secondaryPhoneNumber, " +
                        ":username, " +
                        ":externalId, " +
                        ":password, " +
                        ":version, " +
                        ":unlockAccount, " +
                        ":passwordAuditLimit, " +
                        ":modifiedOn, " +
                        ":modifiedBy)}",
                parameters,
                (rs, rowNum) -> rs.getInt("ID")
        ).stream().findFirst();

        if (userId.isPresent()) {
            return getById(userId.get()).get();
        } else {
            throw new UserSyncFailedException("Failed to reactivate the user.");
        }
    }
}
