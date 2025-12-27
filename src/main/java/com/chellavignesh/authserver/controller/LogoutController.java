package com.chellavignesh.authserver.controller;

import com.chellavignesh.authserver.session.LogoutService;
import com.chellavignesh.authserver.session.dto.LogoutRequest;
import com.chellavignesh.authserver.session.exception.FailedToUpdateSessionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/oauth2/connect/logout")
public class LogoutController {

    private final LogoutService logoutService;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public LogoutController(LogoutService logoutService) {
        this.logoutService = logoutService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> logout(@Valid LogoutRequest requestDto, HttpServletRequest httpRequest, HttpServletResponse httpResponse, Authentication authentication) throws FailedToUpdateSessionException {

        this.logoutHandler.logout(httpRequest, httpResponse, authentication);

        var headers = new HttpHeaders();
        logoutService.logoutSession(requestDto.id_token_hint(), requestDto.client_id(), requestDto.post_logout_redirect_uri());

        String redirectUrlWithErrorCode = addErrorCodeToRedirectUrl(requestDto.post_logout_redirect_uri(), requestDto.error_code());

        headers.add(HttpHeaders.LOCATION, redirectUrlWithErrorCode);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @GetMapping
    public ResponseEntity<?> logoutGet(@RequestParam String id_token_hint, @RequestParam String client_id, @RequestParam String post_logout_redirect_uri, @RequestParam(required = false) String error_code, HttpServletRequest httpRequest, HttpServletResponse httpResponse, Authentication authentication) throws FailedToUpdateSessionException {

        this.logoutHandler.logout(httpRequest, httpResponse, authentication);

        log.debug("Logout request received with id_token_hint: {}, client_id: {}, post_logout_redirect_uri: {}, error_code: {}", id_token_hint, client_id, post_logout_redirect_uri, error_code != null ? error_code : "N/A");

        var headers = new HttpHeaders();

        logoutService.logoutSession(id_token_hint, client_id, post_logout_redirect_uri);

        String redirectUrlWithErrorCode = addErrorCodeToRedirectUrl(post_logout_redirect_uri, error_code);

        headers.add(HttpHeaders.LOCATION, redirectUrlWithErrorCode);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    public String addErrorCodeToRedirectUrl(String redirectUrl, String errorCode) {
        String redirectUrlWithErrorCode = redirectUrl;

        if (StringUtils.hasText(errorCode)) {
            redirectUrlWithErrorCode += "?error_code=" + errorCode;
        }

        return redirectUrlWithErrorCode;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(Exception e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<String> handleMissingRequiredParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body("%s must not be empty".formatted(e.getParameterName()));
    }
}

