package com.chellavignesh.authserver.adminportal.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@RequestMapping(value = {"/forgot-password", "/activate-account", "mobile-forgot-password"})
public class AccountAccessController {
    private static final String APPLICATION_LOGIN_ENTRY_POINT_LINK = "/services/login";
    private static final String ACTIVATE_ACCOUNT_FLOW = "activate-account";
    private static final String FORGOT_PASSWORD_FLOW = "forgot-password";
    private static final String MOBILE_FORGOT_PASSWORD_FLOW = "mobile-forgot-password";
    private static final String MOBILE_FORGOT_USERNAME_FLOW = "mobile-forgot-username";
    private static final String FORGOT_USERNAME_BUTTON_TEXT = "Forgot Username";
    private static final String FORGOT_USERNAME_BUTTON_COLOR = "#2D65B4";
}
