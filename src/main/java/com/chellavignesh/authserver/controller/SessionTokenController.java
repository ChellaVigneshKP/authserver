package com.chellavignesh.authserver.controller;

import com.chellavignesh.authserver.session.sso.SingleSignOnService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.oauth2.core.endpoint.DefaultOAuth2AccessTokenResponseMapConverter;

import java.util.Base64;
import java.util.Map;

@Controller
public class SessionTokenController {

    public static final String AGS_IDP_SESSION = "ags-idp-session";

    private final SingleSignOnService singleSignOnService;

    public SessionTokenController(SingleSignOnService singleSignOnService) {
        this.singleSignOnService = singleSignOnService;
    }

    @GetMapping("/oauth2/sso")
    public ResponseEntity<Map<String, Object>> getTokenFromCookie(@CookieValue(AGS_IDP_SESSION) String sessionValue, HttpServletRequest request) {

        var maybeToken = singleSignOnService.getAccessToken(Base64.getDecoder().decode(sessionValue), request);

        return maybeToken.map(oAuth2AccessTokenResponse -> ResponseEntity.ok(new DefaultOAuth2AccessTokenResponseMapConverter().convert(oAuth2AccessTokenResponse))).orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<Void> handleMissingRequirements() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}

