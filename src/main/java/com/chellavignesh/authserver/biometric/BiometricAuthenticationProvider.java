package com.chellavignesh.authserver.biometric;

import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.util.UUIDUtils;
import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.session.dto.CustomUserDetails;
import com.chellavignesh.authserver.unite.UniteMSCServiceClient;
import com.chellavignesh.authserver.unite.dto.BiometricTokenValidationDto;
import com.chellavignesh.authserver.unite.dto.UniteUserDto;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.authority.mapping.NullAuthoritiesMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Component
public class BiometricAuthenticationProvider implements AuthenticationProvider {
    private static final Logger log = LoggerFactory.getLogger(BiometricAuthenticationProvider.class);
    private final UserService userService;
    private final UniteMSCServiceClient uniteServiceClient;
    private final GrantedAuthoritiesMapper authoritiesMapper = new NullAuthoritiesMapper();

    public BiometricAuthenticationProvider(UserService userService, UniteMSCServiceClient uniteServiceClient) {
        this.userService = userService;
        this.uniteServiceClient = uniteServiceClient;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        var biometricAuthentication = (BiometricAuthenticationToken) authentication;
        String branding = (String) getHttpSession().getAttribute(ApplicationConstants.BRANDING_INFO);
        String biometricToken = (String) biometricAuthentication.getCredentials();
        String deviceId = biometricAuthentication.getDeviceId();

        var dto = new BiometricTokenValidationDto(branding, biometricToken, deviceId);
        UniteUserDto uniteUserDto = uniteServiceClient.validateBiometricToken(dto);
        UUID memberId = UUIDUtils.oracleGuidToSqlUUID(uniteUserDto.getUuidMemberId());

        var userDetails = userService.getByGuidAndBranding(memberId, branding).orElseThrow(() ->
                new BiometricAuthenticationException("User linked to the biometric token not found."));

        var userAuthDetails = userService.getUserAuthDetailsByUsernameAndExternalSourceCode(userDetails.username(), branding).orElseThrow(() ->
                new BiometricAuthenticationException("User linked to the biometric token not found."));

        return this.createSuccessAuthentication(authentication, new CustomUserDetails(userAuthDetails));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BiometricAuthenticationToken.class.isAssignableFrom(authentication);
    }

    public static HttpSession getHttpSession() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs.getRequest().getSession();
    }

    protected Authentication createSuccessAuthentication(Authentication authentication, UserDetails user) {
        BiometricAuthenticationToken result = BiometricAuthenticationToken.authenticated(user, (String) authentication.getCredentials(), authoritiesMapper.mapAuthorities(user.getAuthorities()));
        result.setDetails(authentication.getDetails());
        log.debug("Authenticated user (biometric)");
        return result;
    }
}
