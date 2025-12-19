package com.chellavignesh.authserver.security;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.TokenSettingsService;
import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import com.chellavignesh.authserver.security.exception.RequestDatetimeMissingException;
import com.chellavignesh.authserver.token.TokenService;
import com.chellavignesh.authserver.token.entity.Token;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@Slf4j
public class RequestDatetimeValidator {

    private final ApplicationService applicationService;
    private final Clock clock;
    private final TokenService tokenService;
    private final TokenSettingsService tokenSettingsService;

    public RequestDatetimeValidator(ApplicationService applicationService, Clock clock, TokenService tokenService, TokenSettingsService tokenSettingsService) {

        this.applicationService = applicationService;
        this.clock = clock;
        this.tokenService = tokenService;
        this.tokenSettingsService = tokenSettingsService;
    }

    public boolean validateRequestDatetime(String datetimeHeader, String authorizationHeader) throws RequestDatetimeMissingException {

        if (!StringUtils.hasText(datetimeHeader)) {
            throw new RequestDatetimeMissingException("Request datetime must be provided");
        }

        var pattern = Pattern.compile("^Bearer (.+)$");
        var matcher = pattern.matcher(authorizationHeader);

        if (matcher.matches()) {
            var accessToken = matcher.group(1);
            var token = this.tokenService.getByValue(accessToken, TokenTypeEnum.ACCESS_TOKEN);
            return validate(getApplicationID(token), datetimeHeader);
        }

        return false;
    }

    public boolean validateRequestDatetime(String datetimeHeader, int applicationID) throws RequestDatetimeMissingException {

        if (!StringUtils.hasText(datetimeHeader)) {
            throw new RequestDatetimeMissingException("Request datetime must be provided");
        }

        return validate(applicationID, datetimeHeader);
    }

    private int getApplicationID(Optional<Token> token) {
        return token.orElseThrow().getApplicationId();
    }

    private boolean validate(int applicationID, String datetimeHeader) {

        var application = this.applicationService.getById(applicationID);
        var orgId = application.orElseThrow().getOrgId();

        var tokenSettings = this.tokenSettingsService.getForApp(orgId, applicationID);
        var maxRequestTransitTime = tokenSettings.orElseThrow().getMaxRequestTransitTime();

        var requestDatetime = OffsetDateTime.parse(datetimeHeader);
        var serverDatetime = OffsetDateTime.now(this.clock);

        var diff = Math.abs(ChronoUnit.SECONDS.between(requestDatetime, serverDatetime));

        boolean bResult = diff <= maxRequestTransitTime;

        if (!bResult) {
            String sb = String.format("x-request-datetime: %s \n", requestDatetime) +
                    String.format("server time: %s \n", serverDatetime) +
                    String.format("RequestDatetime diff: %s \n", diff) +
                    String.format("MaxRequestTime: %s \n", maxRequestTransitTime);

            log.error(sb);
        }

        return bResult;
    }
}
