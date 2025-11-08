package com.chellavignesh.authserver.adminportal.application;

import com.chellavignesh.authserver.adminportal.application.dto.CreateApplicationDto;
import com.chellavignesh.authserver.adminportal.application.dto.UpdateApplicationDto;
import com.chellavignesh.authserver.adminportal.application.dto.UpdateTokenSettingsDto;
import com.chellavignesh.authserver.adminportal.application.entity.*;
import com.chellavignesh.authserver.adminportal.application.exception.AppCreationFailedException;
import com.chellavignesh.authserver.adminportal.application.exception.AppSettingsNotFoundException;
import com.chellavignesh.authserver.adminportal.application.exception.ResourceCreationFailedException;
import com.chellavignesh.authserver.adminportal.forgotusername.ForgotUsernameSetting;
import com.chellavignesh.authserver.adminportal.forgotusername.entity.UsernameLookupCriteria;
import com.chellavignesh.authserver.adminportal.util.SecurityUtil;
import com.chellavignesh.authserver.enums.entity.AccessTokenFormatEnum;
import com.chellavignesh.authserver.enums.entity.AlgorithmEnum;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerException;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.Types;
import java.util.*;

@Repository
public class ApplicationRepository {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationRepository.class);
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final SecurityUtil securityUtil;

    @Value("${server.base-path}")
    private String serverBasePath;

    @Autowired
    public ApplicationRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate, SecurityUtil securityUtil) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.securityUtil = securityUtil;
    }

    public boolean exists(Integer orgId, UUID appGuid) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("appGuid", appGuid.toString());

        Integer applicationId = this.namedParameterJdbcTemplate.queryForObject(
                "{call dbo.ApplicationExists(:orgId, :appGuid)}",
                parameters,
                Integer.class
        );
        return applicationId != null;
    }

    public boolean updateApplicationUri(Integer orgId, Integer appId, String uri) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("appId", appId);
        parameters.addValue("uri", uri);
        parameters.addValue("modifiedOn", new Date());
        parameters.addValue("modifiedBy", securityUtil.getTokenUserGuid());
        namedParameterJdbcTemplate.update(
                "{call dbo.UpdateApplicationUri(:orgId, :appId, :uri, :modifiedOn, :modifiedBy)}",
                parameters
        );
        return true;
    }

    public Application create(int orgId, CreateApplicationDto dto, UpdateTokenSettingsDto settingsDto, ForgotUsernameSetting forgotUsernameSetting, AccessTokenFormatEnum accessTokenFormatId) throws AppCreationFailedException {
        String jwkSetUrl = dto.getJwkSetUrl();
        if (jwkSetUrl == null || jwkSetUrl.isEmpty()) {
            jwkSetUrl = serverBasePath + "/oauth2/.well-known/%s/jwks.json";
        }
        Integer jwsAlgorithmId;
        if (Objects.requireNonNull(dto.getAuthMethod()) == AuthFlowEnum.PRIVATE_KEY_JWT) {
            jwsAlgorithmId = AlgorithmEnum.RS256.getValue();
        } else {
            jwsAlgorithmId = AlgorithmEnum.ES256.getValue();
        }
        try {
            SQLServerDataTable forgetUserNameOT = getSqlServerDataTableForForgotUsernameSetting(orgId, null, forgotUsernameSetting);
            var parameters = new MapSqlParameterSource()
                    .addValue("orgId", orgId)
                    .addValue("name", dto.getName())
                    .addValue("description", dto.getDescription())
                    .addValue("uri", "")
                    .addValue("applicationTypeId", dto.getType().getValue())
                    .addValue("authFlowId", dto.getAuthMethod().getValue())
                    .addValue("jwkSetUrl", jwkSetUrl)
                    .addValue("requirePKCE", dto.getAuthMethod() == AuthFlowEnum.PKCE)
                    .addValue("jwsAlgorithmId", jwsAlgorithmId)
                    .addValue("authCodeTimeToLive", settingsDto.getAuthCodeTimeToLive())
                    .addValue("accessTokenTimeToLive", settingsDto.getAccessTokenTimeToLive())
                    .addValue("deviceCodeTimeToLive", settingsDto.getDeviceCodeTimeToLive())
                    .addValue("refreshTokenTimeToLive", settingsDto.getRefreshTokenTimeToLive())
                    .addValue("reuseRefreshTokens", settingsDto.getReuseRefreshTokens())
                    .addValue("accessTokenFormatId", accessTokenFormatId.getValue())
                    .addValue("maxRequestTransitTime", settingsDto.getMaxRequestTransitTime())
                    .addValue("usernameType", (dto.getUsernameType() != null ? dto.getUsernameType().getValue() : null))
                    .addValue("allowForgotUsername", dto.getAllowForgotUsername())
                    .addValue("forgotUserNameSetting", forgetUserNameOT);
            Optional<Integer> appId = namedParameterJdbcTemplate.query(
                    "{call Client.CreateApplication(:orgId, :name, :description, :uri, :applicationTypeId, :authFlowId, :jwkSetUrl, :requirePKCE, :jwsAlgorithmId, :authCodeTimeToLive, :accessTokenTimeToLive, :refreshTokenTimeToLive, :reuseRefreshTokens, :accessTokenFormatId, :deviceCodeTimeToLive, :maxRequestTransitTime, :usernameTyoe, :allowForgotUsername, :forgotUserNameSetting)}", parameters,
                    (RowMapper<Integer>) (rs, rowNum) -> rs.getInt("ID")
            ).stream().findFirst();
            if (appId.isPresent()) {
                return getById(appId.get()).get();
            } else {
                throw new AppCreationFailedException("Failed to create application");
            }
        } catch (SQLServerException ex) {
            logger.error("SQLServerException occurred while creating application: {}", ex.getMessage());
            throw new AppCreationFailedException("Failed to create application due to database error.");
        } catch (JsonProcessingException e) {
            logger.error("JsonProcessingException occurred while creating application: {}", e.getMessage());
            throw new AppCreationFailedException("Failed to create application due to JSON processing error.");
        }

    }

    @NotNull
    private static SQLServerDataTable getSqlServerDataTableForForgotUsernameSetting(Integer orgId, Integer applicationId, ForgotUsernameSetting forgotUsernameSetting) throws SQLServerException, JsonProcessingException {
        SQLServerDataTable forgetUserNameDT = new SQLServerDataTable();
        forgetUserNameDT.setTvpName("[dbo].[ForgetUserNameType]");
        forgetUserNameDT.addColumnMetadata("OrganizationId", Types.INTEGER);
        forgetUserNameDT.addColumnMetadata("ApplicationId", Types.INTEGER);
        forgetUserNameDT.addColumnMetadata("ParamPriority", Types.INTEGER);
        forgetUserNameDT.addColumnMetadata("ParamJson", Types.NVARCHAR);
        for (int i = 0; i < forgotUsernameSetting.getSettings().size(); i++) {
            if (!forgotUsernameSetting.getSettings().get(i).isEmpty()) {
                forgetUserNameDT.addRow(orgId, applicationId, i + 1, forgotUsernameSetting.getSettings().get(i).toJSONString());
            }
        }
        return forgetUserNameDT;
    }

    @Transactional
    public boolean updateApplication(Integer orgId, Integer appId, UpdateApplicationDto updateApplicationDto, ForgotUsernameSetting forgotUsernameSetting) throws SQLServerException, JsonProcessingException {
        SQLServerDataTable forgetUserNameOT = getSqlServerDataTableForForgotUsernameSetting(orgId, appId, forgotUsernameSetting);

        var parameters = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("appId", appId)
                .addValue("name", updateApplicationDto.getName())
                .addValue("description", updateApplicationDto.getDescription())
                .addValue("modifiedOn", new Date())
                .addValue("modifiedBy", securityUtil.getTokenUserGuid())
                .addValue("allowForgotUsername", updateApplicationDto.getAllowForgotUsername())
                .addValue("usernameType", (updateApplicationDto.getUsernameType() != null ? updateApplicationDto.getUsernameType().getValue() : null))
                .addValue("forgotUserNameSetting", forgetUserNameOT)
                .addValue("pinTimeToLive", updateApplicationDto.getPinTimeToLive());
        namedParameterJdbcTemplate.update(
                "{call Client.UpdateApplication(:orgId, :appId, :name, :description, :modifiedOn, :modifiedBy, :allowForgotUsername, :usernameType, :forgotUserNameSetting, :pinTimeToLive)}",
                parameters
        );
        return true;
    }

    public boolean existsByName(String appName, Integer orgId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("appName", appName);
        parameters.addValue("orgId", orgId);
        return namedParameterJdbcTemplate.query(
                "{call dbo.GetApplicationByName(:appName, :orgId)}",
                parameters,
                new ApplicationRowMapper()
        ).stream().findFirst().isPresent();
    }

    public Optional<Application> get(UUID appGuid) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("appGuid", appGuid.toString());
        return namedParameterJdbcTemplate.query(
                "{call Client.GetApplication(:appGuid)}", parameters, new ApplicationRowMapper()
        ).stream().findFirst();
    }

    @Cacheable("application-get-by-id")
    public Optional<Application> getById(Integer appId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("appId", appId);
        return namedParameterJdbcTemplate.query(
                "{call Client.GetApplicationById(:appId)}", parameters, new ApplicationRowMapper()
        ).stream().findFirst();
    }

    @CacheEvict(value = "application-get-by-id", allEntries = true)
    @Scheduled(fixedRateString = "${cache.application.getbyid.ttl}")
    public void evictApplicationGetById() {
        logger.trace("Evicting cache for application-get-by-id");
    }

    @Cacheable("application-get-detail-by-id")
    public Optional<ApplicationDetail> getApplicationDetailById(Integer appId) {
        var storeProc = new GetApplicationDetailProcedure(namedParameterJdbcTemplate.getJdbcTemplate().getDataSource());
        Map<String, Object> results = storeProc.execute(appId);
        ArrayList<ApplicationDetail> appDetails = (ArrayList<ApplicationDetail>) results.get("resultSet1");
        ArrayList<UsernameLookupCriteria> usernameLookupCriteria = (ArrayList<UsernameLookupCriteria>) results.get("resultSet2");
        if (appDetails.isEmpty()) {
            return Optional.empty();
        }
        ApplicationDetail appDetail = appDetails.get(0);
        appDetail.setUsernameLookupCriteria(usernameLookupCriteria);
        return Optional.of(appDetail);
    }

    @CacheEvict(value = "application-get-detail-by-id", allEntries = true)
    @Scheduled(fixedRateString = "${cache.application.getbyid.ttl}")
    public void evictApplicationGetDetailById() {
        logger.trace("Evicting cache for application-get-detail-by-id");
    }

    @Cacheable("application-get-by-client-id")
    public Optional<Application> getByClientId(String clientId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("clientId", clientId);
        return namedParameterJdbcTemplate.query(
                "{call Client.GetApplicationByClientId(:clientId)}", parameters, new ApplicationRowMapper()
        ).stream().findFirst();
    }

    @CacheEvict(value = "application-get-by-client-id", allEntries = true)
    @Scheduled(fixedRateString = "${cache.application.getbyid.ttl}")
    public void evictApplicationGetByClientId() {
        logger.trace("Evicting cache for application-get-by-client-id");
    }

    public boolean inactivateApplication(Integer orgId, Integer appId, Boolean active) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("appGuid", appId);
        parameters.addValue("active", active);
        namedParameterJdbcTemplate.update(
                "{call dbo.UpdateApplicationActivation(:orgId, :appId, :active)}",
                parameters
        );
        return true;
    }

    public List<Application> getAll(Integer orgId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        return namedParameterJdbcTemplate.query(
                "{call dbo.GetApplications(:orgId)}",
                parameters,
                new ApplicationRowMapper()
        );
    }

    @Cacheable("application-get-settings-by-application-id")
    public ApplicationSettings getSettingsByApplicationId(Integer appId) throws AppSettingsNotFoundException {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("appId", appId);
        return namedParameterJdbcTemplate.query(
                "{call Client.GetSettingsByApplicationId(:appId)}",
                parameters,
                new ApplicationSettingsRowMapper()
        ).stream().findFirst().orElseThrow(() -> new AppSettingsNotFoundException("Application settings not found for application id: " + appId));
    }

    @CacheEvict(value = "application-get-settings-by-application-id", allEntries = true)
    @Scheduled(fixedRateString = "${cache.application.getbyid.ttl}")
    public void evictApplicationGetSettingsByApplicationId() {
        logger.trace("Evicting cache for application-get-settings-by-application-id");
    }

    @Cacheable("application-get-redirect-uris")
    public Set<String> getApplicationRedirectUris(Integer appId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("appId", appId);
        return new HashSet<>(namedParameterJdbcTemplate.query(
                "{call Client.GetRedirectUrisByApplicationId(:appId)}",
                parameters,
                (rs, rowNum) -> rs.getString("RedirectUri")
        ));
    }

    @CacheEvict(value = "application-get-redirect-uris", allEntries = true)
    @Scheduled(fixedRateString = "${cache.application.getbyid.ttl}")
    public void evictApplicationRedirectUris() {
        logger.trace("Evicting cache for application-get-redirect-uris");
    }

    @Cacheable("application-get-logout-redirect-uris")
    public Set<String> getApplicationLogoutRedirectUris(Integer appId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("appId", appId);
        return new HashSet<>(namedParameterJdbcTemplate.query(
                "{call Client.GetPostLogoutRedirectUrisByApplicationId(:appId)}",
                parameters,
                (rs, rowNum) -> rs.getString("PostLogoutRedirectUri")
        ));
    }

    @CacheEvict(value = "application-get-logout-redirect-uris", allEntries = true)
    @Scheduled(fixedRateString = "${cache.application.getbyid.ttl}")
    public void evictApplicationLogoutRedirectUris() {
        logger.trace("Evicting cache for application-get-logout-redirect-uris");
    }

    public Resource assignResource(Integer orgId, Integer appId, Integer resourceLibraryId) throws ResourceCreationFailedException {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("appId", appId);
        parameters.addValue("resourceLibraryId", resourceLibraryId);
        Integer resourceId = namedParameterJdbcTemplate.execute(
                "{call Resource.CreateResource(:orgId, :appId, :resourceLibraryId)}",
                parameters,
                cs -> {
                    try (ResultSet rs = cs.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt("ID");
                        }
                        return null;
                    }
                }
        );
        return getResourceById(resourceId).orElseThrow(() -> new ResourceCreationFailedException("Failed to create resource"));
    }

    public Optional<Resource> getResourceById(Integer resourceId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("resourceId", resourceId);
        return namedParameterJdbcTemplate.query(
                "{call Resource.GetResourceById(:resourceId)}", parameters, new ResourceRowMapper()
        ).stream().findFirst();
    }

    public Optional<Resource> getResource(Integer orgId, Integer appId, UUID resourceLibraryGuid) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("appId", appId);
        parameters.addValue("resourceLibraryGuid", resourceLibraryGuid);
        return namedParameterJdbcTemplate.query(
                "{call Resource.GetResource(:orgId, :appId, :resourceLibraryGuid)}", parameters, new ResourceRowMapper()
        ).stream().findFirst();
    }

    public boolean deleteResource(Integer resourceId) {
        namedParameterJdbcTemplate.update(
                "{call Resource.DeleteResource(:resourceId)}",
                new MapSqlParameterSource().addValue("resourceId", resourceId)
        );
        return true;
    }

    public boolean resourceExistsForApplication(Integer orgId, Integer appId, Integer resourceLibraryId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("appId", appId);
        parameters.addValue("resourceLibraryId", resourceLibraryId);
        return namedParameterJdbcTemplate.query(
                "{call Resource.GetResourceByResourceLibraryId(:orgId, :appId, :resourceLibraryId)}",
                parameters,
                new ResourceRowMapper()
        ).stream().findFirst().isPresent();
    }

    @Cacheable("application-get-all-assigned-resources-by-client-id")
    public List<ApplicationResource> getAllAssignedResourcesByClientId(String clientId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("clientId", clientId);
        return namedParameterJdbcTemplate.query(
                "{call Client.getAllResourcesByClientId(:clientId)}",
                parameters,
                new ApplicationResourceRowMapper()
        );
    }

    @CacheEvict(value = "application-get-all-assigned-resources-by-client-id", allEntries = true)
    @Scheduled(fixedRateString = "${cache.application.getbyid.ttl}")
    public void evictApplicationGetAllAssignedResourcesByClientId() {
        logger.trace("Evicting cache for application-get-all-assigned-resources-by-client-id");
    }

    public List<ApplicationResource> getAllApplicationResources(Integer appId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("appId", appId);
        return namedParameterJdbcTemplate.query(
                "{call Client.getAllResourcesByAppId(:appId)}",
                parameters,
                new ApplicationResourceRowMapper()
        );
    }
}
