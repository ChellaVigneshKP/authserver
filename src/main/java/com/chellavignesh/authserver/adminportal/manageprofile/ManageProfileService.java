package com.chellavignesh.authserver.adminportal.manageprofile;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.forgotusername.exception.InvalidUserSessionException;
import com.chellavignesh.authserver.adminportal.forgotusername.exception.InvalidUserSessionSecurityException;
import com.chellavignesh.authserver.adminportal.manageprofile.exception.TokenExpiredException;
import com.chellavignesh.authserver.cms.BrandUrlMappingService;
import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import com.chellavignesh.authserver.security.RequestDatetimeValidator;
import com.chellavignesh.authserver.security.exception.RequestDatetimeInvalidException;
import com.chellavignesh.authserver.security.exception.RequestDatetimeMissingException;
import com.chellavignesh.authserver.session.AuthSessionService;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.session.sso.SingleSignOnService;
import com.chellavignesh.authserver.session.sso.exception.FingerprintFailedException;
import com.chellavignesh.authserver.session.sso.exception.InactiveAuthSessionException;
import com.chellavignesh.authserver.session.sso.exception.InvalidSingleSignOnCookieException;
import com.chellavignesh.authserver.token.TokenService;
import com.chellavignesh.authserver.token.entity.Token;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ManageProfileService {

    private final SingleSignOnService singleSignOnService;
    private final TokenService tokenService;
    private final AuthSessionService authSessionService;
    private final BrandUrlMappingService brandUrlMappingService;
    private final RequestDatetimeValidator requestDatetimeValidator;
    private final ApplicationService applicationService;

    @Autowired
    public ManageProfileService(SingleSignOnService singleSignOnService, TokenService tokenService, AuthSessionService authSessionService, BrandUrlMappingService brandUrlMappingService, RequestDatetimeValidator requestDatetimeValidator, ApplicationService applicationService) {

        this.singleSignOnService = singleSignOnService;
        this.tokenService = tokenService;
        this.authSessionService = authSessionService;
        this.brandUrlMappingService = brandUrlMappingService;
        this.requestDatetimeValidator = requestDatetimeValidator;
        this.applicationService = applicationService;
    }

    public String getRedirect(String sourceUrl, AuthSession authSession) {
        String redirectUri = authSession.getRedirectUri();

        if (redirectUri == null) {
            log.warn("RedirectUri is null in AuthSession - sessionId: {}, applicationId: {}, using sourceUrl as fallback", authSession.getSessionId(), authSession.getApplicationId());
            redirectUri = sourceUrl;
        }

        if (redirectUri.endsWith("/")) {
            redirectUri = redirectUri.substring(0, redirectUri.length() - 1);
        }

        String p = "surl=%s".formatted(sourceUrl);
        return "%s?p=%s".formatted(redirectUri, Base64.getEncoder().encodeToString(p.getBytes(StandardCharsets.UTF_8)));
    }

    public String getRedirect(String sourceUrl, String errorCode, AuthSession authSession) {
        String redirectUri = authSession.getRedirectUri();

        if (redirectUri == null) {
            log.warn("RedirectUri is null in AuthSession - sessionId: {}, applicationId: {}, using sourceUrl as fallback", authSession.getSessionId(), authSession.getApplicationId());
            redirectUri = sourceUrl;
        }

        if (redirectUri.endsWith("/")) {
            redirectUri = redirectUri.substring(0, redirectUri.length() - 1);
        }

        String p = "surl=%s&errors.codes=%s".formatted(sourceUrl, errorCode);
        return "%s?p=%s".formatted(redirectUri, Base64.getEncoder().encodeToString(p.getBytes(StandardCharsets.UTF_8)));
    }

    public Map<String, String> decodeRequestParameter(String p) {
        byte[] decoded = Base64.getDecoder().decode(p);
        String decodedStr = new String(decoded, StandardCharsets.UTF_8);

        String[] splitStr = decodedStr.split("&");
        Map<String, String> parameters = new HashMap<>();

        for (String str : splitStr) {
            String[] param = str.split("=", 2);
            if (param.length == 2) {
                String key = URLDecoder.decode(param[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(param[1], StandardCharsets.UTF_8);
                parameters.put(key, value);
            }
        }

        return parameters;
    }

    public void validateRequestDatetime(HttpServletRequest request, int applicationID, String datetime) throws RequestDatetimeInvalidException {

        try {
            if (!requestDatetimeValidator.validateRequestDatetime(datetime, applicationID)) {
                throw new RequestDatetimeInvalidException("Invalid x-request-datetime");
            }

        } catch (RequestDatetimeMissingException e) {
            throw new RequestDatetimeInvalidException(e.getMessage());
        }
    }

    public AuthSession validateSessionInCookie(HttpServletRequest request, String sessionValue, String requestDatetime) throws InvalidUserSessionSecurityException, InactiveAuthSessionException, FingerprintFailedException {

        log.debug("Validating session from cookie");

        AuthSession session = null;

        try {
            session = singleSignOnService.getAuthSessionFromCookie(Base64.getDecoder().decode(sessionValue)).get();

            log.debug("AuthSession retrieved from cookie - sessionId: {}", session.getSessionId());

        } catch (InvalidSingleSignOnCookieException e) {
            log.error("Failed to get AuthSession from cookie: {}", e.getMessage());
            throw new InvalidUserSessionSecurityException(e.getMessage(), e);
        }

        if (session.getAuthSessionId() == null) {
            log.error("AuthSessionId in cookie is null.");
            throw new InvalidUserSessionSecurityException("AuthSession in cookie is null.");
        }

        validateSessionFingerprint(session, request, requestDatetime);

        return session;
    }

    public AuthSession validateSessionFromToken(Token token) throws InvalidUserSessionException {

        var optionalAuthSession = authSessionService.getBySessionId(token.getSessionId());

        if (optionalAuthSession.isEmpty()) {
            throw new InvalidUserSessionException("User session cannot be found.");
        }

        return optionalAuthSession.get();
    }

    public Token getTokenFromAuthHeader(String authorizationHeader) throws InvalidUserSessionException {

        var pattern = Pattern.compile("^Bearer (.+)$");
        var matcher = pattern.matcher(authorizationHeader);

        if (matcher.matches()) {
            var accessToken = matcher.group(1);

            var token = this.tokenService.getByValue(accessToken, TokenTypeEnum.ACCESS_TOKEN);

            if (token.isPresent()) {
                return token.get();
            }
        }

        throw new InvalidUserSessionException("User Session not found");
    }

    public void validateSessionFingerprint(AuthSession authSession, HttpServletRequest request, String requestDatetime) throws FingerprintFailedException, InactiveAuthSessionException {

        singleSignOnService.validateSessionFingerprintAndThrow(authSession, request, requestDatetime);
    }

    public void validateToken(Token token, AuthSession authSession) throws TokenExpiredException {

        if (!tokenService.isTokenActive(token, authSession)) {
            throw new TokenExpiredException("Token expired.");
        }
    }
}
