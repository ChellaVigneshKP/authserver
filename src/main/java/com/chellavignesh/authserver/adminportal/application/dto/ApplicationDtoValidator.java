package com.chellavignesh.authserver.adminportal.application.dto;

import com.chellavignesh.authserver.adminportal.forgotusername.UsernameLookupFieldService;
import com.chellavignesh.authserver.adminportal.range.RangeCache;
import com.chellavignesh.authserver.enums.entity.ApplicationTypeEnum;
import com.chellavignesh.authserver.enums.entity.AuthFlowEnum;
import com.chellavignesh.authserver.enums.entity.RangeTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.util.*;

@Component
public class ApplicationDtoValidator implements Validator {

    @Autowired
    RangeCache rangeCache;

    @Autowired
    UsernameLookupFieldService usernameLookupFieldService;

    @Override
    public boolean supports(Class<?> clazz) {
        return clazz.equals(CreateApplicationDto.class)
                || clazz.equals(UpdateApplicationDto.class)
                || clazz.equals(UpdateTokenSettingsDto.class);
    }

    @Override
    public void validate(Object target, Errors errors) {
        if (target instanceof CreateApplicationDto dto) {
            if (dto.getType() == null) {
                errors.rejectValue("type", "Invalid", "Must include application type");
            }
            if ((dto.getType() == ApplicationTypeEnum.MOBILE || dto.getType() == ApplicationTypeEnum.WEB) && dto.getAuthMethod() != AuthFlowEnum.PKCE) {
                errors.rejectValue("authMethod", "Invalid", "Mobile and Web applications must use PKCE");
            }
            if (dto.getType() == ApplicationTypeEnum.SERVER && (dto.getAuthMethod() != AuthFlowEnum.CLIENT_SECRET_JWT && dto.getAuthMethod() != AuthFlowEnum.PRIVATE_KEY_JWT)) {
                errors.rejectValue("authMethod", "Invalid", "Server applications must use Client Secret JWT or Private Key JWT");
            }

            var forgotUsernameSettings = dto.getForgotUsernameSettings();
            validateForgotUsernameSettings(forgotUsernameSettings, errors);
        }

        if (target instanceof UpdateTokenSettingsDto dto) {
            if (!Objects.isNull(dto.getAccessTokenTimeToLive())) {
                if (!rangeCache.getRange(RangeTypeEnum.ACCESS_TOKEN_TTL).getRange().contains(dto.getAccessTokenTimeToLive())) {
                    errors.rejectValue("accessTokenTimeToLive", "Invalid", "Access token time to live must be within the range of " + rangeCache.getRange(RangeTypeEnum.ACCESS_TOKEN_TTL).getRange());
                }
            } else {
                errors.rejectValue("accessTokenTimeToLive", "Invalid", "Access token time to live must be provided");
            }
            if (!Objects.isNull(dto.getAuthCodeTimeToLive())) {
                if (!rangeCache.getRange(RangeTypeEnum.AUTH_CODE_TTL).getRange().contains(dto.getAuthCodeTimeToLive())) {
                    errors.rejectValue("authCodeTimeToLive", "Invalid", "Auth code time to live must be within the range of " + rangeCache.getRange(RangeTypeEnum.AUTH_CODE_TTL).getRange());
                } else {
                    errors.rejectValue("authCodeTimeToLive", "Invalid", "Auth code time to live must be provided");
                }
            }

            if (!Objects.isNull(dto.getDeviceCodeTimeToLive())
                    && !rangeCache.getRange(RangeTypeEnum.DEVICE_CODE_TTL).getRange().contains(dto.getDeviceCodeTimeToLive())) {
                errors.rejectValue("deviceCodeTimeToLive", "Invalid", "Device code time to live must be within the range of " + rangeCache.getRange(RangeTypeEnum.DEVICE_CODE_TTL).getRange());
            }

            if (!Objects.isNull(dto.getRefreshTokenTimeToLive())) {
                if (!rangeCache.getRange(RangeTypeEnum.REFRESH_TOKEN_TTL).getRange().contains(dto.getRefreshTokenTimeToLive())) {
                    errors.rejectValue("refreshTokenTimeToLive", "Invalid", "Refresh token time to live must be within the range of " + rangeCache.getRange(RangeTypeEnum.REFRESH_TOKEN_TTL).getRange());
                } else {
                    errors.rejectValue("refreshTokenTimeToLive", "Invalid", "Refresh token time to live must be provided");
                }
            }

            if (Objects.isNull(dto.getReuseRefreshTokens())) {
                errors.rejectValue("reuseRefreshTokens", "Invalid", "Reuse refresh tokens must be provided");
            }

            if (!Objects.isNull(dto.getMaxRequestTransitTime())) {
                if (!rangeCache.getRange(RangeTypeEnum.MAX_REQUEST_TRANSIT_TIME).getRange().contains(dto.getMaxRequestTransitTime())) {
                    errors.rejectValue("maxRequestTransitTime", "Invalid", "Max request transit time must be within the range of " + rangeCache.getRange(RangeTypeEnum.MAX_REQUEST_TRANSIT_TIME).getRange());
                }
            } else {
                errors.rejectValue("maxRequestTransitTime", "Invalid", "Max request transit time must be provided");
            }
        }
    }

    private void validateForgotUsernameSettings(List<ForgotUsernameSettingDto> forgotUsernameSettings, Errors errors) {
        if (forgotUsernameSettings == null || forgotUsernameSettings.isEmpty()) {
            return;
        }
        forgotUsernameSettings.forEach(
                setting -> {
                    if (setting.getPriority() == null) {
                        errors.rejectValue("priority", "Invalid", "Priority must be provided for each forgot username setting");
                    }
                }
        );

        if (!arePrioritiesValid(forgotUsernameSettings)) {
            errors.rejectValue("forgotUsernameSettings", "Invalid", "Invalid priorities");
        }

        forgotUsernameSettings.forEach(settings ->
                Arrays.stream(settings.getLookupCriteria()).forEach(lookupField -> {
                    if (!isValidUsernameLookupField(lookupField)) {
                        errors.rejectValue("forgotUsernameSettings", "Invalid", "Invalid lookup field: " + lookupField);
                    }
                })
        );
    }

    private boolean arePrioritiesValid(List<ForgotUsernameSettingDto> forgotUsernameSettings) {
        int numObjects = forgotUsernameSettings.size();
        Set<Integer> seenPriorities = new HashSet<>();
        for (ForgotUsernameSettingDto obj : forgotUsernameSettings) {
            int priority = obj.getPriority();
            if (seenPriorities.contains(priority)) {
                return false;
            }
            seenPriorities.add(priority);
            if (priority < 1 || priority > numObjects) {
                return false;
            }
        }
        return true;
    }

    Boolean isValidUsernameLookupField(String lookupField) {
        return usernameLookupFieldService.getAllAsMap().get(lookupField) != null;
    }
}
