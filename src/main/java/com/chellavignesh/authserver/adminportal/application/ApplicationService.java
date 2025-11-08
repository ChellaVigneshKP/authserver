package com.chellavignesh.authserver.adminportal.application;

import com.chellavignesh.authserver.adminportal.application.dto.CreateApplicationDto;
import com.chellavignesh.authserver.adminportal.application.dto.UpdateTokenSettingsDto;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.application.exception.AppCreationFailedException;
import com.chellavignesh.authserver.adminportal.credential.CredentialService;
import com.chellavignesh.authserver.adminportal.forgotusername.ForgotUsernameSetting;
import com.chellavignesh.authserver.adminportal.forgotusername.entity.UsernameLookupCriteria;
import com.chellavignesh.authserver.adminportal.organization.OrganizationRepository;
import com.chellavignesh.authserver.adminportal.range.RangeCache;
import com.chellavignesh.authserver.enums.entity.AccessTokenFormatEnum;
import com.chellavignesh.authserver.enums.entity.ApplicationTypeEnum;
import com.chellavignesh.authserver.enums.entity.RangeTypeEnum;
import com.chellavignesh.authserver.enums.entity.UsernameTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ApplicationService {
    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

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
