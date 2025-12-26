package com.chellavignesh.authserver.config;

import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.entity.UserAuthDetails;
import com.chellavignesh.authserver.cms.BrandUrlMappingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final UserService userService;
    private final BrandUrlMappingService brandUrlMappingService;

    public CustomAuthenticationFailureHandler(UserService userService, BrandUrlMappingService brandUrlMappingService) {
        this.userService = userService;
        this.brandUrlMappingService = brandUrlMappingService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {

        if (exception instanceof InternalAuthenticationServiceException && exception.getCause() instanceof LockedException) {

            log.warn("User's account is locked, redirecting to forgot password page");

            getRedirectStrategy().sendRedirect(request, response, "%s/forgot-password".formatted(brandUrlMappingService.getUrlByBrand(brandUrlMappingService.getBrandFromRequest(request))));
            return;
        }

        if (exception instanceof BadCredentialsException) {
            request.getSession().setAttribute("loginError", true);

            String username = request.getParameter("username");
            Optional<UserAuthDetails> userDetails = getUserAuthDetails(username);

            if (userDetails.isPresent() && Boolean.TRUE.equals(userDetails.get().getCredentialLocked())) {
                log.warn("User's account is locked, redirecting to forgot password page");

                getRedirectStrategy().sendRedirect(request, response, "%s/forgot-password".formatted(brandUrlMappingService.getUrlByBrand(brandUrlMappingService.getBrandFromRequest(request))));
                return;
            }
        }

        log.warn("User entered invalid credentials, redirecting to login page");

        getRedirectStrategy().sendRedirect(request, response, "%s/login?error".formatted(brandUrlMappingService.getUrlByBrand(brandUrlMappingService.getBrandFromRequest(request))));
    }

    private Optional<UserAuthDetails> getUserAuthDetails(String username) {
        Optional<UserAuthDetails> userDetails = Optional.empty();

        final String[] usernameBrandArray = username.split(System.lineSeparator());

        if (usernameBrandArray.length < 2 || usernameBrandArray[1].isBlank()) {
            log.warn("External Id Flag is enabled but username is missing line separator " + "separating brand from login, blocking login");
        } else {
            userDetails = userService.updateAccessFailedCountWithExternalSourceCode(usernameBrandArray[0], usernameBrandArray[1], 0);
        }

        return userDetails;
    }
}

