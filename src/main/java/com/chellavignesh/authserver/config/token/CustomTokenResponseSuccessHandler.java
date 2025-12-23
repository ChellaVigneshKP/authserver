package com.chellavignesh.authserver.config.token;

import com.chellavignesh.authserver.adminportal.util.DateUtil;
import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import com.chellavignesh.authserver.token.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.util.CollectionUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class CustomTokenResponseSuccessHandler implements AuthenticationSuccessHandler {

    private final TokenService tokenService;

    private static final Logger log = LoggerFactory.getLogger(CustomTokenResponseSuccessHandler.class);

    private final HttpMessageConverter<OAuth2AccessTokenResponse> accessTokenHttpResponseConverter = new OAuth2AccessTokenResponseHttpMessageConverter();

    public CustomTokenResponseSuccessHandler(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

        try {
            OAuth2AccessTokenAuthenticationToken accessTokenAuthentication = (OAuth2AccessTokenAuthenticationToken) authentication;

            OAuth2AccessToken accessToken = accessTokenAuthentication.getAccessToken();

            OAuth2RefreshToken refreshToken = accessTokenAuthentication.getRefreshToken();

            Map<String, Object> additionalParameters = accessTokenAuthentication.getAdditionalParameters();

            OAuth2AccessTokenResponse.Builder builder = OAuth2AccessTokenResponse.withToken(accessToken.getTokenValue()).tokenType(accessToken.getTokenType()).scopes(accessToken.getScopes());

            if (accessToken.getIssuedAt() != null && accessToken.getExpiresAt() != null) {
                builder.expiresIn(ChronoUnit.SECONDS.between(accessToken.getIssuedAt(), accessToken.getExpiresAt()));
            }

            if (refreshToken != null) {
                builder.refreshToken(refreshToken.getTokenValue());
            }

            if (!CollectionUtils.isEmpty(additionalParameters)) {
                builder.additionalParameters(additionalParameters);
            }

            performCustomLogic(accessToken, additionalParameters, builder);

            OAuth2AccessTokenResponse accessTokenResponse = builder.build();

            ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);

            this.accessTokenHttpResponseConverter.write(accessTokenResponse, null, httpResponse);

        } catch (IOException ex) {
            log.error("CustomTokenResponseSuccessHandler: {}", ex.getMessage());
            throw ex;
        }
    }

    private void performCustomLogic(OAuth2AccessToken accessToken, Map<String, Object> additionalParameters, OAuth2AccessTokenResponse.Builder builder) {

        var token = tokenService.getByValue(accessToken.getTokenValue(), TokenTypeEnum.ACCESS_TOKEN);

        if (token.isPresent()) {
            additionalParameters = new HashMap<>(additionalParameters);
            additionalParameters.put("signing_key", token.get().getSigningKey());

            Date lastLogin = (Date) getHttpSession().getAttribute(ApplicationConstants.LAST_LOGIN);

            if (lastLogin != null) {
                additionalParameters.put("last_login", DateUtil.getISO8601Date(lastLogin));
            }

            // last_login attribute has served its purpose,
            // no need to keep it in the session
            getHttpSession().removeAttribute(ApplicationConstants.LAST_LOGIN);

            builder.additionalParameters(additionalParameters);
        }
    }

    public static HttpSession getHttpSession() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();

        return attr.getRequest().getSession();
    }
}
