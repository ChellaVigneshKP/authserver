package com.chellavignesh.authserver.adminportal.application;

import com.chellavignesh.authserver.adminportal.application.dto.UpdateTokenSettingsDto;
import com.chellavignesh.authserver.adminportal.application.entity.TokenSettings;
import com.chellavignesh.authserver.adminportal.application.exception.TokenSettingsCreationFailedException;
import com.chellavignesh.authserver.adminportal.range.RangeCache;
import com.chellavignesh.authserver.enums.entity.AccessTokenFormatEnum;
import com.chellavignesh.authserver.enums.entity.RangeTypeEnum;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class TokenSettingsService {
    private final RangeCache rangeCache;
    private final TokenSettingsRepository settingsRepository;

    public TokenSettingsService(RangeCache rangeCache, TokenSettingsRepository settingsRepository) {
        this.rangeCache = rangeCache;
        this.settingsRepository = settingsRepository;
    }

    public TokenSettings createSettings(Integer orgId, Integer appId, UpdateTokenSettingsDto settingsDto) throws TokenSettingsCreationFailedException {
        if (Objects.isNull(settingsDto.getDeviceCodeTimeToLive())) {
            settingsDto.setDeviceCodeTimeToLive(rangeCache.getRange(RangeTypeEnum.DEVICE_CODE_TTL).getMin());
        }
        return settingsRepository.createSettings(orgId, appId, settingsDto, AccessTokenFormatEnum.REFERENCE);
    }

    public boolean updateSettings(Integer orgId, Integer appId, UpdateTokenSettingsDto settingsDto) {
        return settingsRepository.updateSettings(orgId, appId, settingsDto);
    }

    public Optional<TokenSettings> getForApp(Integer orgId, Integer appId) {
        return settingsRepository.getForApp(orgId, appId);
    }

    public Boolean exists(Integer orgId, Integer appId) {
        return settingsRepository.existsForApp(orgId, appId);
    }

}
