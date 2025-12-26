package com.chellavignesh.authserver.security;

import com.chellavignesh.authserver.adminportal.externalsource.ExternalSourceService;
import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.security.branding.BrandingAwareRequestWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@Slf4j
public class BrandingRequestBodyFilter extends OncePerRequestFilter {

    private final String loginPagePath;
    private final ExternalSourceService externalSourceService;

    public BrandingRequestBodyFilter(@Value("#{environment.getProperty(T(com.chellavignesh.authserver.config.ApplicationConstants).SERVLET_CONTEXT_PATH)}") final String contextPath, @Autowired final ExternalSourceService externalSourceService) {

        this.loginPagePath = contextPath + ApplicationConstants.LOGIN_PAGE_PATH;
        this.externalSourceService = externalSourceService;
        log.info("Will filter POST for path: {}", loginPagePath);
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain) throws ServletException, IOException {

        if (!(HttpMethod.POST.matches(request.getMethod()) && StringUtils.endsWith(request.getRequestURI(), loginPagePath))) {

            log.debug("Request not qualify for filtering. URI: {}, METHOD: {}", request.getRequestURI(), request.getMethod());
            filterChain.doFilter(request, response);
            return;
        }

        // we are in login
        final var brandIdFromRequest = Optional.ofNullable(request.getSession(false)).map(session -> session.getAttribute(ApplicationConstants.BRANDING_INFO)).filter(String.class::isInstance).map(String.class::cast).filter(StringUtils::isNotBlank);

        if (brandIdFromRequest.isPresent()) {

            final var brandInDatabase = externalSourceService.findBySourceCode(brandIdFromRequest.get());

            log.debug("Session attribute {} with value: {}, isPresentInDatabase: {}", ApplicationConstants.BRANDING_INFO, brandIdFromRequest, brandInDatabase);

            // we wrap request into BrandingAwareRequestWrapper,
            // so we can extract brand from username only when filter is enabled
            if (brandInDatabase.isPresent()) {
                log.info("Branding {} found in request and is present in database", brandIdFromRequest);

                filterChain.doFilter(new BrandingAwareRequestWrapper(request, brandIdFromRequest.get()), response);
                return;
            }
        }

        // error state, only reaching here when brand is not in request or not in database
        // log for all scenarios (brand enabled or not, fail only when branding enabled)
        String errorMessage;

        if (StringUtils.isNotBlank(brandIdFromRequest.orElse(null))) {
            // in session but not in database
            log.error("Brand {} not exists in database", brandIdFromRequest);
            errorMessage = "Bad branding in session " + ApplicationConstants.BRANDING_INFO;
        } else {
            // not in session
            log.error("Missing {} in session", ApplicationConstants.BRANDING_INFO);
            errorMessage = "Missing branding in session: " + ApplicationConstants.BRANDING_INFO;
        }

        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.getWriter().println(errorMessage);
    }
}

