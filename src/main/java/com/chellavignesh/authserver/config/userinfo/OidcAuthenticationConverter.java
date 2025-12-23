package com.chellavignesh.authserver.config.userinfo;

import com.chellavignesh.authserver.token.SignatureService;
import com.chellavignesh.authserver.token.exception.SignatureVerificationFailedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;

public class OidcAuthenticationConverter implements AuthenticationConverter {

    private static final Logger logger = LoggerFactory.getLogger(OidcAuthenticationConverter.class);

    private final SignatureService signatureService;

    public OidcAuthenticationConverter(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {

        var req = new ContentCachingRequestWrapper(request);

        req.getParameterMap(); // trigger request caching
        var body = req.getContentAsString();

        var signatureHeader = req.getHeader("x-signature");

        if (!StringUtils.hasText(signatureHeader)) {
            if (signatureService.isSignatureRequired()) {
                throw new OAuth2AuthenticationException("Missing request body signature.");
            } else {
                logger.warn("Missing request body signature");
            }
        } else {
            try {
                var authorizationHeader = req.getHeader(HttpHeaders.AUTHORIZATION);

                var verified = this.signatureService.verifySignature(authorizationHeader, signatureHeader, body.getBytes(StandardCharsets.UTF_8));

                if (!verified) {
                    throw new OAuth2AuthenticationException("Invalid request body signature.");
                }
            } catch (SignatureVerificationFailedException e) {
                throw new RuntimeException("Could not validate request body signature.", e);
            }
        }

        BearerTokenAuthentication principal = (BearerTokenAuthentication) request.getUserPrincipal();

        OidcUserInfo userInfo = new OidcUserInfo(principal.getTokenAttributes());

        return new OidcUserInfoAuthenticationToken(principal, userInfo);
    }
}
