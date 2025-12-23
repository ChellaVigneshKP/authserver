package com.chellavignesh.authserver.config.authorization;

import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.enums.entity.BiometricTypeEnum;
import com.chellavignesh.authserver.session.fingerprint.ClientFingerprint;
import com.chellavignesh.authserver.session.fingerprint.ClientFingerprintParser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponseType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.time.ZoneOffset;
import java.util.*;

@Service
public class CustomOAuth2AuthorizationCodeRequestAuthenticationConverter implements AuthenticationConverter {

    private static final Logger logger = LoggerFactory.getLogger(CustomOAuth2AuthorizationCodeRequestAuthenticationConverter.class);

    private static final String DEFAULT_ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1";

    private static final String PKCE_ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc7636#section-4.4.1";

    private static final Authentication ANONYMOUS_AUTHENTICATION = new AnonymousAuthenticationToken("anonymous", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    private static final RequestMatcher OIDC_REQUEST_MATCHER = createOidcRequestMatcher();
    private static final String PARAMETER_REQUEST_DATETIME = "x-request-datetime";

    private final boolean isFingerprintingEnabled;
    private final boolean isFingerprintingRefererDisabled;

    public CustomOAuth2AuthorizationCodeRequestAuthenticationConverter(@Value("${toggles.fingerprinting.enabled}") boolean isFingerprintingEnabled, @Value("${toggles.fingerprint.referer.disabled}") boolean isFingerprintingRefererDisabled) {

        this.isFingerprintingEnabled = isFingerprintingEnabled;
        this.isFingerprintingRefererDisabled = isFingerprintingRefererDisabled;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {

        logger.debug("Starting OAuth2 authorization request conversion for: {}", request.getRequestURL());

        try {
            if (!"GET".equals(request.getMethod()) && !OIDC_REQUEST_MATCHER.matches(request)) {
                logger.debug("Request method {} not supported or OIDC matcher failed", request.getMethod());
                return null;
            }

            MultiValueMap<String, String> parameters = "GET".equals(request.getMethod()) ? CustomOAuth2EndpointUtils.getQueryParameters(request) : CustomOAuth2EndpointUtils.getFormParameters(request);

            logger.debug("Extracted {} parameters from request", parameters.size());

            // response_type (REQUIRED)
            String responseType = parameters.getFirst(OAuth2ParameterNames.RESPONSE_TYPE);
            if (!StringUtils.hasText(responseType) || parameters.get(OAuth2ParameterNames.RESPONSE_TYPE).size() != 1) {
                throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.RESPONSE_TYPE);
            } else if (!OAuth2AuthorizationResponseType.CODE.getValue().equals(responseType)) {
                throwError(OAuth2ErrorCodes.UNSUPPORTED_RESPONSE_TYPE, OAuth2ParameterNames.RESPONSE_TYPE);
            }

            String authorizationUri = request.getRequestURL().toString();

            // client_id (REQUIRED)
            String clientId = parameters.getFirst(OAuth2ParameterNames.CLIENT_ID);
            if (!StringUtils.hasText(clientId) || parameters.get(OAuth2ParameterNames.CLIENT_ID).size() != 1) {
                throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.CLIENT_ID);
            }

            Authentication principal = SecurityContextHolder.getContext().getAuthentication();
            if (principal == null) {
                principal = ANONYMOUS_AUTHENTICATION;
            }

            // redirect_uri (OPTIONAL)
            String redirectUri = parameters.getFirst(OAuth2ParameterNames.REDIRECT_URI);
            if (StringUtils.hasText(redirectUri) && parameters.get(OAuth2ParameterNames.REDIRECT_URI).size() != 1) {
                throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.REDIRECT_URI);
            }

            // scope (OPTIONAL)
            Set<String> scopes = null;
            String scope = parameters.getFirst(OAuth2ParameterNames.SCOPE);
            if (StringUtils.hasText(scope) && parameters.get(OAuth2ParameterNames.SCOPE).size() != 1) {
                throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.SCOPE);
            }
            if (StringUtils.hasText(scope)) {
                scopes = new HashSet<>(Arrays.asList(StringUtils.delimitedListToStringArray(scope, " ")));
            }

            // state (RECOMMENDED)
            String state = parameters.getFirst(OAuth2ParameterNames.STATE);
            if (StringUtils.hasText(state) && parameters.get(OAuth2ParameterNames.STATE).size() != 1) {
                throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.STATE);
            }

            // PKCE
            String codeChallenge = parameters.getFirst(PkceParameterNames.CODE_CHALLENGE);
            if (StringUtils.hasText(codeChallenge) && parameters.get(PkceParameterNames.CODE_CHALLENGE).size() != 1) {
                throwError(OAuth2ErrorCodes.INVALID_REQUEST, PkceParameterNames.CODE_CHALLENGE, PKCE_ERROR_URI);
            }

            String codeChallengeMethod = parameters.getFirst(PkceParameterNames.CODE_CHALLENGE_METHOD);
            if (StringUtils.hasText(codeChallengeMethod) && parameters.get(PkceParameterNames.CODE_CHALLENGE_METHOD).size() != 1) {
                throwError(OAuth2ErrorCodes.INVALID_REQUEST, PkceParameterNames.CODE_CHALLENGE_METHOD, PKCE_ERROR_URI);
            }

            Map<String, Object> additionalParameters = new HashMap<>();
            parameters.forEach((key, value) -> {
                if (!key.equals(OAuth2ParameterNames.RESPONSE_TYPE) &&
                        !key.equals(OAuth2ParameterNames.CLIENT_ID) &&
                        !key.equals(OAuth2ParameterNames.REDIRECT_URI) &&
                        !key.equals(OAuth2ParameterNames.SCOPE) &&
                        !key.equals(OAuth2ParameterNames.STATE)) {
                    additionalParameters.put(key, value.size() == 1 ? value.get(0) : value.toArray(new String[0]));
                }
            });

            logger.debug("Performing custom logic");
            performCustomLogic(request, parameters);
            logger.debug("Custom logic completed successfully");

            return new OAuth2AuthorizationCodeRequestAuthenticationToken(authorizationUri, clientId, principal, redirectUri, state, scopes, additionalParameters);

        } catch (Exception e) {
            logger.error("Exception in OAuth2 authorization request conversion: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void performCustomLogic(HttpServletRequest request, MultiValueMap<String, String> parameters) {

        if (this.isFingerprintingEnabled) {
            ClientFingerprint clientFingerprint = generateClientFingerprint(request, parameters);
            request.getSession().setAttribute(ApplicationConstants.CLIENT_FINGERPRINT, clientFingerprint.getBytes());
        }

        if (parameters.containsKey(ApplicationConstants.BRANDING_INFO)) {
            String brandingInfo = parameters.getFirst(ApplicationConstants.BRANDING_INFO);
            request.getSession().setAttribute("branding", brandingInfo);
        }

        if (parameters.containsKey(OAuth2ParameterNames.CLIENT_ID)) {
            String clientId = parameters.getFirst(OAuth2ParameterNames.CLIENT_ID);
            request.getSession().setAttribute(ApplicationConstants.CLIENT_ID, clientId);
        }

        if (parameters.containsKey(ApplicationConstants.ERROR_CODE_PARAMETER)) {
            String errorCode = parameters.getFirst(ApplicationConstants.ERROR_CODE_PARAMETER);
            request.getSession().setAttribute(ApplicationConstants.EXTERNAL_AUTH_ERROR_CODE, errorCode);
        } else {
            request.getSession().removeAttribute(ApplicationConstants.EXTERNAL_AUTH_ERROR_CODE);
        }
        if (parameters.containsKey(ApplicationConstants.BIOMETRIC_TYPE)) {
            String biometricType = parameters.getFirst(ApplicationConstants.BIOMETRIC_TYPE);
            BiometricTypeEnum bioType = BiometricTypeEnum.fromString(biometricType);
            if (bioType != null) {
                request.getSession().setAttribute(ApplicationConstants.BIOMETRIC_TYPE, bioType);
            }
        }
        if (parameters.containsKey(ApplicationConstants.P_VALUE)) {
            String pValue = parameters.getFirst(ApplicationConstants.P_VALUE);
            request.getSession().setAttribute(ApplicationConstants.P_VALUE, pValue);
        }
    }

    private ClientFingerprint generateClientFingerprint(HttpServletRequest request, MultiValueMap<String, String> parameters) {
        String sb = "GENERATION FINGERPRINT\n" + " - RequestDatetime: " + parameters.getFirst(PARAMETER_REQUEST_DATETIME) + "\n" +
                " - AcceptLanguage: " + request.getHeader(HttpHeaders.ACCEPT_LANGUAGE) + "\n" +
                " - UserAgent: " + request.getHeader(HttpHeaders.USER_AGENT) + "\n" +
                " - Referer: " + request.getHeader(HttpHeaders.REFERER) + "\n";
        logger.debug(sb);
        String requestDatetime = parameters.getFirst(PARAMETER_REQUEST_DATETIME);

        if (!StringUtils.hasText(requestDatetime) || parameters.get(PARAMETER_REQUEST_DATETIME).size() != 1) {
            logger.error("Invalid request parameter: {}", PARAMETER_REQUEST_DATETIME);
            throwError(OAuth2ErrorCodes.INVALID_REQUEST, PARAMETER_REQUEST_DATETIME);
        }

        ZoneOffset zoneOffset = ClientFingerprintParser.parseZoneOffset(requestDatetime);
        if (zoneOffset == null) {
            throwError(OAuth2ErrorCodes.INVALID_REQUEST, PARAMETER_REQUEST_DATETIME);
        }

        String acceptLanguage = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (!StringUtils.hasText(acceptLanguage)) {
            throwError(OAuth2ErrorCodes.INVALID_REQUEST, HttpHeaders.ACCEPT_LANGUAGE);
        }

        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (!StringUtils.hasText(userAgent)) {
            throwError(OAuth2ErrorCodes.INVALID_REQUEST, HttpHeaders.USER_AGENT);
        }

        String refererHost = "";
        if (!this.isFingerprintingRefererDisabled) {
            String referer = request.getHeader(HttpHeaders.REFERER);
            if (!StringUtils.hasText(referer)) {
                throwError(OAuth2ErrorCodes.INVALID_REQUEST, HttpHeaders.REFERER);
            }

            refererHost = ClientFingerprintParser.parseRefererHost(referer);
            if (!StringUtils.hasText(refererHost)) {
                throwError(OAuth2ErrorCodes.INVALID_REQUEST, HttpHeaders.REFERER);
            }
        }

        return new ClientFingerprint(zoneOffset, acceptLanguage, userAgent, refererHost);
    }

    private static RequestMatcher createOidcRequestMatcher() {

        RequestMatcher postMethodMatcher = request -> "POST".equals(request.getMethod());

        RequestMatcher responseTypeMatcher = request -> request.getParameter(OAuth2ParameterNames.RESPONSE_TYPE) != null;

        RequestMatcher openidScopeMatcher = request -> {
            String scope = request.getParameter(OAuth2ParameterNames.SCOPE);
            return StringUtils.hasText(scope) && scope.contains(OidcScopes.OPENID);
        };

        return new AndRequestMatcher(postMethodMatcher, responseTypeMatcher, openidScopeMatcher);
    }

    private static void throwError(String errorCode, String parameterName) {
        throwError(errorCode, parameterName, DEFAULT_ERROR_URI);
    }

    private static void throwError(String errorCode, String parameterName, String errorUri) {

        OAuth2Error error = new OAuth2Error(errorCode, "OAuth 2.0 Parameter: " + parameterName, errorUri);

        throw new OAuth2AuthorizationCodeRequestAuthenticationException(error, null);
    }
}

