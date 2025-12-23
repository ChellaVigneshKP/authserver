package com.chellavignesh.authserver.config.introspect;

import com.chellavignesh.authserver.config.RequestBodyDecoder;
import com.chellavignesh.authserver.config.exception.OAuth2AuthenticationBadRequestException;
import com.chellavignesh.authserver.config.exception.RequestBodyDecodeFailureException;
import com.chellavignesh.authserver.token.SignatureService;
import com.chellavignesh.authserver.token.exception.SignatureVerificationFailedException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class IntrospectionAuthenticationConverter implements AuthenticationConverter {

    private static final Logger logger = LoggerFactory.getLogger(IntrospectionAuthenticationConverter.class);

    private final SignatureService signatureService;

    public IntrospectionAuthenticationConverter(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {

        // TODO: Should use OAuth2TokenIntrospectionAuthenticationConverter converter
        // and then just add signature validation on top

        BearerTokenAuthentication principal = (BearerTokenAuthentication) request.getUserPrincipal();

        String tokenToIntrospect;

        logger.trace("IntrospectionAuthenticationConverter::convert principal: {}", principal);
        logger.trace("IntrospectionAuthenticationConverter::convert authType: {}", request.getAuthType());

        var req = new ContentCachingRequestWrapper(request);
        req.getParameterMap();
        var body = req.getContentAsString();

        var signatureHeader = req.getHeader("x-signature");

        if (!org.springframework.util.StringUtils.hasText(signatureHeader)) {
            if (signatureService.isSignatureRequired()) {
                throw new OAuth2AuthenticationBadRequestException("Missing request body signature.");
            } else {
                logger.warn("Missing request body signature");
            }
        } else {
            try {
                var authorizationHeader = req.getHeader(HttpHeaders.AUTHORIZATION);

                var verified = this.signatureService.verifySignature(authorizationHeader, signatureHeader, body.getBytes(StandardCharsets.UTF_8));

                if (!verified) {
                    throw new OAuth2AuthenticationBadRequestException("Invalid request body signature.");
                }
            } catch (SignatureVerificationFailedException e) {
                throw new RuntimeException("Could not validate request body signature.", e);
            }
        }

        // TODO: Breaks all OAuth2 clients. Should be moved into provider
        // to only change res_access claim
        try {
            Map<String, String> data = RequestBodyDecoder.decode(body);

            tokenToIntrospect = data.get("token");

            if (tokenToIntrospect == null || tokenToIntrospect.isEmpty()) {
                throw new OAuth2AuthenticationBadRequestException("Token missing from request body.");
            }

            String resUrl = data.get("res_url");
            String resMethod = data.get("res_method");

            if (StringUtils.isEmpty(resUrl) || StringUtils.isEmpty(resMethod)) {
                throw new OAuth2AuthenticationBadRequestException("res_url/res_method missing in request.");
            }

        } catch (RequestBodyDecodeFailureException e) {
            throw new RuntimeException(e);
        }

        return new OAuth2TokenIntrospectionAuthenticationToken(tokenToIntrospect, principal, "access_token", new HashMap<>());
    }
}
