package com.chellavignesh.authserver.adminportal.user;

import com.chellavignesh.authserver.adminportal.organization.entity.OrganizationGroupPermission;
import com.chellavignesh.authserver.adminportal.organization.entity.OrganizationGroupPermissionRowMapper;
import com.chellavignesh.authserver.adminportal.hosted.HostedAccountRevocationRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@Slf4j
public class UserRepository {

    @NotNull
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @NotNull
    private final SecurityUtil securityUtil;

    @NotNull
    private final HostedAccountRevocationRepository hostedAccountRevocations;

    @Autowired
    public UserRepository(@NotNull NamedParameterJdbcTemplate namedParameterJdbcTemplate, @NotNull SecurityUtil securityUtil,
                          @NotNull HostedAccountRevocationRepository hostedAccountRevocations) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.securityUtil = securityUtil;
        this.hostedAccountRevocations = hostedAccountRevocations;
    }

    public Optional<Profile> getProfile(UUID profileGuid) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("profileGuid", profileGuid == null ? null : profileGuid.toString());

        return namedParameterJdbcTemplate.query("{call Person.GetProfile(:profileGuid)}", parameters, new ProfileRowMapper()).stream().findFirst();
    }

    @Transactional(rollbackFor = Exception.class)
    public User create(CreateUserDto dto, String hashPassword, Integer version, int groupId, UUID externalId, int disallowedRecentPasswordCount) throws UserCreationFailedException {

        log.debug("Creating user in the database with syncFlag: {}", dto.getSyncFlag());

        var parameters = new MapSqlParameterSource()
                .addValue("orgId", dto.getOrgId())
                .addValue("firstName", dto.getFirstName())
                .addValue("lastName", dto.getLastName())
                .addValue("username", dto.getUsername())
                .addValue("email", dto.getEmail())
                .addValue("phoneNumber", dto.getPhoneNumber())
                .addValue("password", java.util.HexFormat.of().parseHex(hashPassword), Types.VARBINARY)
                .addValue("version", version)
                .addValue("groupId", groupId)
                .addValue("memberId", dto.getMemberId() == null ? null : dto.getMemberId().toString())
                .addValue("loginId", dto.getLoginId() == null ? null : dto.getLoginId().toString())
                .addValue("externalId", externalId == null ? null : externalId.toString())
                .addValue("secondaryPhoneNumber", dto.getSecondaryPhoneNumber())
                .addValue("disallowedRecentPasswordCount", disallowedRecentPasswordCount)
                .addValue("syncFlag", dto.getSyncFlag());

        // Do not log credential parameters.

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

        // Person.UpdateUserProfile rewrites Profile/RowGuid and every Credential.RowGuid from
        // these values. Profile editing must never move identities, so bindings stay fixed.
        Integer suffix = dto.getSuffix() != null ? dto.getSuffix().getValue() : currentSuffix(userGuid);
        var parameters = new MapSqlParameterSource().addValue("userGuid", userGuid == null ? null : userGuid.toString()).addValue("firstName", dto.getFirstName()).addValue("lastName", dto.getLastName()).addValue("title", dto.getTitle()).addValue("middleInitial", dto.getMiddleInitial()).addValue("phoneNumber", dto.getPhoneNumber()).addValue("suffix", suffix).addValue("memberId", (Object) null).addValue("loginId", (Object) null).addValue("modifiedOn", new Date()).addValue("modifiedBy", securityUtil.getTokenUserGuid()).addValue("secondaryPhoneNumber", dto.getSecondaryPhoneNumber());

        Optional<Integer> appId = namedParameterJdbcTemplate.query("{call Person.UpdateUserProfile(" + ":userGuid," + ":firstName," + ":lastName," + ":title," + ":middleInitial," + ":phoneNumber," + ":suffix," + ":memberId," + ":loginId," + ":modifiedOn," + ":modifiedBy," + ":secondaryPhoneNumber" + ")}",
                parameters,
                (rs, _) -> rs.getInt("ID")).stream().findFirst();

        if (appId.isPresent()) {
            return getById(appId.get()).get();
        } else {
            throw new UserUpdateFailedException("User does not exist.");
        }
    }

    private Integer currentSuffix(UUID userGuid) {
        return namedParameterJdbcTemplate.queryForObject(
                "SELECT Suffix FROM Person.Profile WHERE RowGuid=:userGuid",
                new MapSqlParameterSource("userGuid", userGuid.toString()), Integer.class);
    }

    public User updateForChangeProfilePage(ChangeProfileDto dto, UUID userGuid) throws UserUpdateFailedException, UserNotFoundException {

        Optional<UserDetails> userDetails = getByGuid(userGuid);

        if (userDetails.isEmpty()) {
            throw new UserNotFoundException("User does not exist.");
        }

        log.debug("Updating user in the database with userGuid: {}", userGuid);

        var parameters = new MapSqlParameterSource().addValue("userGuid", userGuid == null ? null : userGuid.toString()).addValue("email", dto.getEmail()).addValue("phoneNumber", dto.getPhoneNumber()).addValue("secondaryPhoneNumber", dto.getSecondaryPhoneNumber()).addValue("modifiedBy", securityUtil.getTokenUserGuid()).addValue("firstName", dto.getFirstName()).addValue("lastName", dto.getLastName());

        log.debug("Updating user in the database with parameters: {}", parameters);

        Optional<Integer> appId = namedParameterJdbcTemplate.query("{call Person.UpdateUserProfileForChangeProfilePage(" + ":userGuid," + ":email," + ":phoneNumber," + ":secondaryPhoneNumber," + ":modifiedBy," + ":firstName," + ":lastName" + ")}",
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
                .addValue("userGuid", userGuid == null ? null : userGuid.toString())
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

    public User updateSecuritySettings(UpdateUserSecuritySettingsDto dto, UUID userGuid) throws UserUpdateFailedException {

        var parameters = new MapSqlParameterSource()
                .addValue("userGuid", userGuid == null ? null : userGuid.toString())
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

    @Transactional(rollbackFor = Exception.class)
    public User updateMetadata(UpdateUserMetadataDto dto, UUID userGuid) throws UserUpdateFailedException, UserNotFoundException {

        Optional<UserDetails> userDetails = getByGuid(userGuid);

        if (userDetails.isPresent()) {

            var parameters = new MapSqlParameterSource()
                    .addValue("profileId", userDetails.get().id());

            Set<String> systemManagedKeys = new HashSet<>(namedParameterJdbcTemplate.queryForList(
                    "SELECT [Key] FROM Person.Metadata WHERE ProfileId=:profileId AND SystemManaged=1",
                    parameters, String.class));
            namedParameterJdbcTemplate.update(
                    "DELETE FROM Person.Metadata WHERE ProfileId=:profileId AND SystemManaged=0",
                    parameters
            );
            Map<String, Object> metadata = dto.getMetaData() == null ? Map.of() : dto.getMetaData();
            for (String key : metadata.keySet()) {
                if (systemManagedKeys.contains(key)) continue;
                parameters = new MapSqlParameterSource()
                        .addValue("profileId", userDetails.get().id())
                        .addValue("key", key)
                        .addValue("value", metadata.get(key))
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
                .addValue("userGuid", userGuid == null ? null : userGuid.toString())
                .addValue("status", status)
                .addValue("modifiedOn", new Date())
                .addValue("modifiedBy", securityUtil.getTokenUserGuid());

        Optional<Integer> appId = namedParameterJdbcTemplate.query(
                "{call Person.UpdateUserStatus(:userGuid, :status, :modifiedOn, :modifiedBy)}",
                parameters,
                (rs, rowNum) -> rs.getInt("ID")
        ).stream().findFirst();

        if (appId.isPresent()) {
            // A deactivated or deleted member must lose every existing session, token and grant
            // immediately; reactivation deliberately starts without restoring prior access.
            if (status != 1) {
                hostedAccountRevocations.revokeAllForProfile(userGuid);
            }
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
        parameters.addValue("loginId", loginId == null ? null : loginId.toString());

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
        parameters.addValue("userGuid", userGuid == null ? null : userGuid.toString());

        return namedParameterJdbcTemplate.query(
                "{call Person.GetUserDetailsByUserId(:userGuid)}",
                parameters,
                new UserDetailsRowMapper()
        ).stream().findFirst();
    }

    public Optional<UserDetails> getByGuidAndBranding(UUID userGuid, String branding) {
        log.info("Fetching user in the database by userGuid: {} and branding: {}", userGuid, branding);

        var parameters = new MapSqlParameterSource()
                .addValue("userGuid", userGuid == null ? null : userGuid.toString())
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
        parameters.addValue("organizationId", organizationId.orElse(null) == null ? null : organizationId.orElse(null).toString());
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
        // Capture the stable credential with its password/version in the same SQL row.
        var rows = namedParameterJdbcTemplate.query("""
                SELECT p.*,c.RowGuid AS LoginId,c.UserName,c.Password,c.Version,c.AccessFailedCount,
                       c.CredentialLocked,c.LastLogin,c.Status AS CredentialStatus
                FROM Person.Credential c JOIN Person.Profile p ON p.ProfileId=c.ProfileId
                JOIN dbo.ExternalSource e ON e.SourceId=c.ExternalId
                WHERE c.UserName=:username AND e.SourceCode=:sourceCode
                """, MapSqlParameterBuilder.of("username",username,"sourceCode",externalSourceCode),
                (row, index) -> {
                    var details = UserAuthDetails.fromResult(row);
                    details.setLoginId(UUID.fromString(row.getString("LoginId")));
                    return details;
                });
        return rows.size() == 1 ? Optional.of(rows.getFirst()) : Optional.empty();
    }

    public Optional<UserAuthDetails> getAuthDetailsByLoginId(UUID loginId) {
        var rows=namedParameterJdbcTemplate.query("""
                SELECT p.*,c.RowGuid AS LoginId,c.UserName,c.Password,c.Version,c.AccessFailedCount,
                  c.CredentialLocked,c.LastLogin,c.Status AS CredentialStatus
                FROM Person.Credential c JOIN Person.Profile p ON p.ProfileId=c.ProfileId
                WHERE c.RowGuid=:login
                """,new MapSqlParameterSource("login",loginId.toString()),(row,index)->{
                    var details=UserAuthDetails.fromResult(row);
                    details.setLoginId(UUID.fromString(row.getString("LoginId")));return details;
                });
        return rows.size()==1?Optional.of(rows.getFirst()):Optional.empty();
    }

    public Optional<UserDetails> getDetailsByUsernameAndBranding(String username, String branding) {
        var rows = namedParameterJdbcTemplate.query(
                "{call Person.GetUserByUsernameAndBranding(:username, :branding)}",
                new MapSqlParameterSource("username",username).addValue("branding",branding),
                new UserDetailsRowMapper());
        return rows.size() == 1 ? Optional.of(rows.getFirst()) : Optional.empty();
    }

    /** Only biometric proofs lack a username; never choose an arbitrary sibling for them. */
    public Optional<UserDetails> getUniqueBiometricIdentity(UUID userGuid, String branding) {
        var identities = namedParameterJdbcTemplate.query("""
                SELECT c.RowGuid,c.UserName FROM Person.Credential c
                JOIN Person.Profile p ON p.ProfileId=c.ProfileId
                JOIN dbo.ExternalSource e ON e.SourceId=c.ExternalId
                WHERE p.RowGuid=:userGuid AND e.SourceCode=:branding
                  AND p.Status=1 AND c.Status=1 AND c.CredentialLocked=0
                """,new MapSqlParameterSource("userGuid",userGuid.toString()).addValue("branding",branding),
                (row,index) -> new String[]{row.getString("RowGuid"),row.getString("UserName")});
        if (identities.size()!=1) return Optional.empty();
        var identity=identities.getFirst();
        return getDetailsByUsernameAndBranding(identity[1],branding)
                .filter(user -> userGuid.equals(user.rowGuid()) && user.loginId().equals(UUID.fromString(identity[0])));
    }

    /** Management selection is explicit and may read an otherwise authorized inactive profile. */
    public Optional<UserDetails> getManagementDetails(UUID profileId, UUID loginId) {
        var parameters=new MapSqlParameterSource("profile",profileId.toString())
                .addValue("login",loginId==null?null:loginId.toString());
        var rows=namedParameterJdbcTemplate.query("""
            SELECT COUNT(*) OVER() AS Results,p.ProfileId,p.FirstName,p.LastName,c.UserName,p.RowGuid,p.Email,p.Title,p.MiddleInitial,p.Suffix,
            p.PhoneNumber,p.TwoFactorEnabled,p.Status,p.SecondaryPhoneNumber,p.SyncFlag,c.SyncFlag AS CredSyncFlag,
            c.RowGuid AS LoginId,es.SourceCode AS Branding,po.OrganizationId AS ProfileOrgId,
            o.RowGuid AS ProfileOrgGuid,o.RowGuid AS OrgId,og.RowGuid AS GroupGuid,og.GroupName
            FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId
            JOIN dbo.ExternalSource es ON es.SourceId=c.ExternalId
            JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId AND po.Status=1
            JOIN Partner.Organization o ON o.OrganizationId=po.OrganizationId AND o.Status=1
            OUTER APPLY(SELECT g.* FROM Person.ProfileGroup pg JOIN Partner.OrganizationGroup g ON g.OrganizationGroupId=pg.OrganizationGroupId
                WHERE pg.ProfileId=p.ProfileId AND g.Status=1 AND g.OrganizationId=po.OrganizationId) og
            WHERE p.RowGuid=:profile AND (:login IS NULL OR c.RowGuid=:login)
            """,parameters,new UserDetailsRowMapper());
        if(rows.size()>1)throw com.chellavignesh.authserver.adminportal.organization.role.RoleFailure.credential();
        if(rows.size()==1 && !Integer.valueOf(1).equals(namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM Person.Credential WHERE RowGuid=:login",
                new MapSqlParameterSource("login",rows.getFirst().loginId().toString()),Integer.class)))
            throw com.chellavignesh.authserver.adminportal.organization.role.RoleFailure.credential();
        return rows.stream().findFirst();
    }

    public Optional<UserDetails> getDetailsByLoginId(UUID loginId) {
        var identities = namedParameterJdbcTemplate.query("""
                SELECT c.UserName,e.SourceCode FROM Person.Credential c
                JOIN dbo.ExternalSource e ON e.SourceId=c.ExternalId WHERE c.RowGuid=:loginId
                """,new MapSqlParameterSource("loginId",loginId.toString()),
                (row,index) -> new String[]{row.getString("UserName"),row.getString("SourceCode")});
        if (identities.size()!=1) return Optional.empty();
        var identity=identities.getFirst();
        return getDetailsByUsernameAndBranding(identity[0],identity[1])
                .filter(user -> loginId.equals(user.loginId()));
    }

    public Optional<ProfileOrganization> getProfileOrganizationByProfileId(final int profileId) {
        return namedParameterJdbcTemplate.query(
                "{call Person.GetProfileOrganizationByProfileId(:profileId)}",
                MapSqlParameterBuilder.of("profileId", profileId),
                ProfileOrganization.MAPPER
        ).stream().findFirst();
    }

    public boolean isManagementIdentityActive(UUID user, UUID organization, UUID credential) {
        return !namedParameterJdbcTemplate.query("""
                SELECT actor.ProfileId FROM Person.ActiveManagementIdentity actor
                JOIN Person.Credential c ON c.ProfileId=actor.ProfileId AND c.Status=1 AND c.CredentialLocked=0
                WHERE actor.UserGuid=:user AND actor.OrganizationGuid=:organization AND c.RowGuid=:credential
                """,new MapSqlParameterSource("user",user.toString()).addValue("organization",organization.toString())
                .addValue("credential",credential.toString()),(row,_)->row.getInt(1)).isEmpty();
    }

    /** Live organization-scoped grants for authorization after the shared mutation lock. */
    public Set<String> getCurrentManagementPermissions(UUID userGuid, UUID organizationGuid) {
        var parameters = new MapSqlParameterSource("userGuid", userGuid.toString())
                .addValue("organizationGuid", organizationGuid.toString());
        return new HashSet<>(namedParameterJdbcTemplate.query("""
                SELECT DISTINCT permission.PermissionKey
                FROM Person.Profile actor
                JOIN Person.ProfileOrganization membership ON membership.ProfileId=actor.ProfileId AND membership.Status=1
                JOIN Partner.Organization organization ON organization.OrganizationId=membership.OrganizationId AND organization.Status=1
                JOIN Person.ProfileGroup assignment ON assignment.ProfileId=actor.ProfileId
                JOIN Partner.OrganizationGroup role ON role.OrganizationGroupId=assignment.OrganizationGroupId
                    AND role.OrganizationId=organization.OrganizationId AND role.Status=1
                JOIN Partner.OrganizationGroupPermission permission ON permission.OrganizationGroupId=role.OrganizationGroupId
                    AND permission.OrganizationId=organization.OrganizationId AND permission.Status=1
                WHERE actor.RowGuid=:userGuid AND actor.Status=1 AND organization.RowGuid=:organizationGuid
                AND EXISTS(SELECT 1 FROM Person.ActiveManagementIdentity valid WHERE valid.ProfileId=actor.ProfileId AND valid.OrganizationId=organization.OrganizationId)
                AND (organization.OrganizationId=1 OR NOT EXISTS(SELECT 1 FROM dbo.PermissionCatalog catalog
                    WHERE catalog.PermissionKey=permission.PermissionKey COLLATE Latin1_General_100_BIN2 AND catalog.PlatformOnly=1))
                """, parameters, (row, _) -> row.getString(1)));
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
    @Transactional(rollbackFor=Exception.class)
    public User reactivateAccountWithPassword(CreateUserProfileDto dto, UUID userGuid, UUID externalId, int passwordVersion, int unlockAccount, ReactivationAuditActor actualActor) throws UserSyncFailedException {

        if (actualActor == null) throw new org.springframework.security.access.AccessDeniedException("An authenticated audit actor is required.");
        new com.chellavignesh.authserver.security.ManagementMutationLock(namedParameterJdbcTemplate.getJdbcTemplate()).acquire();
        var selection=new MapSqlParameterSource("profile",userGuid.toString())
                .addValue("source",externalId.toString())
                .addValue("organization",dto.getOrgId()==null?null:dto.getOrgId().toString())
                .addValue("login",dto.getCredential().getLoginId()==null?null:dto.getCredential().getLoginId().toString());
        if(dto.getProfile().getMemberId()!=null && !userGuid.equals(dto.getProfile().getMemberId()))
            throw com.chellavignesh.authserver.adminportal.organization.role.RoleFailure.credential();
        var matches=namedParameterJdbcTemplate.query("""
            SELECT c.RowGuid FROM Person.Credential c JOIN Person.Profile p ON p.ProfileId=c.ProfileId
            JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId AND po.Status=1
            JOIN Partner.Organization o ON o.OrganizationId=po.OrganizationId AND o.Status=1 AND o.RowGuid=:organization
            WHERE p.RowGuid=:profile AND p.Status=0 AND c.ExternalId=:source AND (:login IS NULL OR c.RowGuid=:login)
            """,selection,(rs,n)->UUID.fromString(rs.getString(1)));
        if(matches.size()!=1)throw com.chellavignesh.authserver.adminportal.organization.role.RoleFailure.credential();
        UUID selected=matches.getFirst();
        new com.chellavignesh.authserver.session.CredentialAuthenticationLock(namedParameterJdbcTemplate).acquire(selected);
        if(!Integer.valueOf(1).equals(namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM Person.Credential WHERE RowGuid=:login",new MapSqlParameterSource("login",selected.toString()),Integer.class)))
            throw com.chellavignesh.authserver.adminportal.organization.role.RoleFailure.credential();
        new com.chellavignesh.authserver.adminportal.hosted.HostedAccountRevocationRepository(namedParameterJdbcTemplate).revokeAllForLogin(selected);
        var parameters = new MapSqlParameterSource()
                .addValue("userGuid", userGuid == null ? null : userGuid.toString())
                .addValue("firstName", dto.getProfile().getFirstName())
                .addValue("lastName", dto.getProfile().getLastName())
                .addValue("email", dto.getProfile().getEmail())
                .addValue("phoneNumber", dto.getProfile().getPhoneNumber())
                .addValue("memberGuid", userGuid.toString())
                .addValue("loginGuid", selected.toString())
                .addValue("secondaryPhoneNumber", dto.getProfile().getSecondaryPhoneNumber())
                .addValue("username", dto.getCredential().getUsername())
                .addValue("password", java.util.HexFormat.of().parseHex(dto.getCredential().getPasswordHash()), Types.VARBINARY)
                .addValue("version", passwordVersion)
                .addValue("externalId", externalId == null ? null : externalId.toString())
                .addValue("unlockAccount", unlockAccount)
                .addValue("passwordAuditLimit", 12)
                .addValue("modifiedOn", new Date())
                .addValue("modifiedBy", actualActor.value());

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
