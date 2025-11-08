package com.chellavignesh.authserver.adminportal.application;

import com.chellavignesh.authserver.adminportal.application.dto.UpdateTokenSettingsDto;
import com.chellavignesh.authserver.adminportal.application.entity.TokenSettings;
import com.chellavignesh.authserver.adminportal.application.entity.TokenSettingsRowMapper;
import com.chellavignesh.authserver.adminportal.application.exception.TokenSettingsCreationFailedException;
import com.chellavignesh.authserver.adminportal.util.SecurityUtil;
import com.chellavignesh.authserver.enums.entity.AccessTokenFormatEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
@Slf4j
public class TokenSettingsRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final SecurityUtil securityUtil;

    @Autowired
    public TokenSettingsRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate, SecurityUtil securityUtil) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.securityUtil = securityUtil;
    }

    public TokenSettings createSettings(Integer orgId, Integer appId, UpdateTokenSettingsDto settingsDto, AccessTokenFormatEnum accessTokenFormatId) throws TokenSettingsCreationFailedException {
        var parameters = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("appId", appId)
                .addValue("authCodeTimeToLive", settingsDto.getAccessTokenTimeToLive())
                .addValue("accessTokenTimeToLive", settingsDto.getAccessTokenTimeToLive())
                .addValue("refreshTokenTimeToLive", settingsDto.getRefreshTokenTimeToLive())
                .addValue("reuseRefreshTokens", settingsDto.getReuseRefreshTokens())
                .addValue("accessTokenFormatId", accessTokenFormatId.getValue())
                .addValue("maxRequestTransitTime", settingsDto.getMaxRequestTransitTime());

        Optional<Integer> tsId = namedParameterJdbcTemplate.query(
                "{call Client.CreateTokenSetting(:orgId, :appId, :authCodeTimeToLive, :accessTokenTimeToLive, :refreshTokenTimeToLive, :reuseRefreshTokens, :accessTokenFormatId, :maxRequestTransitTime)}", parameters,
                (rs, rowNum) -> rs.getInt("ID")
        ).stream().findFirst();

        if (tsId.isPresent()) {
            return getById(tsId.get()).get();
        } else {
            throw new TokenSettingsCreationFailedException("Failed to create token settings");
        }
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = {"token-settings-get-for-app", "registered-client-by-client-id"}, key = "#appId"),
            @CacheEvict(cacheNames = "token-settings-get-by-id", allEntries = true)
    })
    public boolean updateSettings(Integer orgId, Integer appId, UpdateTokenSettingsDto settingsDto) {
        var parameters = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("appId", appId)
                .addValue("authCodeTimeToLive", settingsDto.getAuthCodeTimeToLive())
                .addValue("accessTokenTimeToLive", settingsDto.getAccessTokenTimeToLive())
                .addValue("deviceCodeTimeToLive", settingsDto.getDeviceCodeTimeToLive())
                .addValue("refreshTokenTimeToLive", settingsDto.getRefreshTokenTimeToLive())
                .addValue("reuseRefreshTokens", settingsDto.getReuseRefreshTokens())
                .addValue("maxRequestTransitTime", settingsDto.getMaxRequestTransitTime())
                .addValue("modifiedOn", new Date())
                .addValue("modifiedBy", securityUtil.getTokenUserGuid());

        namedParameterJdbcTemplate.update(
                "{call Client.UpdateTokenSetting(:orgId, :appId, :authCodeTimeToLive, :accessTokenTimeToLive, :refreshTokenTimeToLive, :reuseRefreshTokens, :deviceCodeTimeToLive, :maxRequestTransitTime, :modifiedOn, :modifiedBy)}",
                parameters
        );
        log.info("Updated token settings for app {} and org {}", appId, orgId);
        return true;
    }

    @Cacheable("token-settings-get-for-app")
    public Optional<TokenSettings> getForApp(Integer orgId, Integer appId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("appId", appId);
        return namedParameterJdbcTemplate.query(
                "{call Client.GetTokenSettingForApp(:orgId, :appId)}",
                parameters,
                new TokenSettingsRowMapper()
        ).stream().findFirst();
    }

    @Cacheable("token-settings-get-by-id")
    public Optional<TokenSettings> getById(Integer tokenSettingId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("tokenSettingId", tokenSettingId);
        return namedParameterJdbcTemplate.query(
                "{call Client.GetTokenSettingById(:tokenSettingId)}",
                parameters,
                new TokenSettingsRowMapper()
        ).stream().findFirst();
    }

    public boolean existsForApp(Integer orgId, Integer appId) {
        var paramters = new MapSqlParameterSource();
        paramters.addValue("orgId", orgId);
        paramters.addValue("appId", appId);
        List<Integer> tokenSettingIdList = namedParameterJdbcTemplate.queryForList(
                "{call dbo.TokenSettingExistsForApp(:orgId, :appId)}",
                paramters,
                Integer.class
        );
        return !tokenSettingIdList.isEmpty();
    }
}
