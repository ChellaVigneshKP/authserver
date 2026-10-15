package com.chellavignesh.authserver.local;

import com.chellavignesh.authserver.adminportal.application.*;
import com.chellavignesh.authserver.adminportal.application.dto.*;
import com.chellavignesh.authserver.adminportal.forgotusername.ForgotUsernameSetting;
import com.chellavignesh.authserver.adminportal.user.UserRepository;
import com.chellavignesh.authserver.adminportal.user.dto.CreateUserDto;
import com.chellavignesh.authserver.enums.entity.*;
import com.chellavignesh.authserver.session.PasswordEncoderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit local-only creation of missing development identities. Never resets credentials. */
@Component
@Profile("local")
@ConditionalOnProperty(name = "app.local.bootstrap.enabled", havingValue = "true")
public class LocalDevelopmentBootstrap implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalDevelopmentBootstrap.class);
    private final UserRepository users;
    private final ApplicationRepository applications;
    private final RedirectUriRepository redirects;
    private final PostLogoutRedirectUriRepository logoutRedirects;
    private final PasswordEncoderFactory passwords;
    private final String username;
    private final String password;
    private final String callback;

    public LocalDevelopmentBootstrap(UserRepository users, ApplicationRepository applications,
            RedirectUriRepository redirects, PostLogoutRedirectUriRepository logoutRedirects,
            PasswordEncoderFactory passwords, @Value("${app.local.admin.username}") String username,
            @Value("${app.local.admin.password}") String password,
            @Value("${app.local.callback-url}") String callback) {
        this.users=users; this.applications=applications; this.redirects=redirects;
        this.logoutRedirects=logoutRedirects; this.passwords=passwords;
        this.username=username; this.password=password; this.callback=callback;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... arguments) throws Exception {
        if (password.length() < 12 || username.isBlank()) {
            throw new IllegalArgumentException("Local bootstrap requires a username and a 12+ character password");
        }
        if (users.getUserAuthDetailsByUsernameAndExternalSourceCode(username, "idp").isEmpty()) {
            CreateUserDto user=new CreateUserDto();
            user.setOrgId(1); user.setFirstName("Ascensus"); user.setLastName("Admin");
            user.setUsername(username); user.setEmail("admin@example.test");
            user.setPhoneNumber("+19999999999"); user.setBranding("idp"); user.setSyncFlag(false);
            String raw=Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
            var created = users.create(user, passwords.encode(raw), PasswordEncoderFactory.currentVersion, 1,
                    UUID.fromString("11111111-1111-1111-1111-111111111111"), 12);
            // This newly created local fixture has no external MFA enrollment.
            // Existing users are never modified by bootstrap.
            var settings = new com.chellavignesh.authserver.adminportal.user.dto.UpdateUserSecuritySettingsDto();
            settings.setTwoFactorEnabled(false);
            users.updateSecuritySettings(settings, created.getRowGuid());
            log.info("Created missing local administrator '{}'", username);
        }
        var existing=applications.getAll(1).stream().filter(a -> "Admin Portal".equals(a.getName())).findFirst();
        if (existing.isEmpty()) {
            CreateApplicationDto dto=new CreateApplicationDto();
            dto.setName("Admin Portal"); dto.setDescription("Local development PKCE client");
            dto.setType(ApplicationTypeEnum.WEB); dto.setAuthMethod(AuthFlowEnum.PKCE);
            dto.setUsernameType(UsernameTypeEnum.USERNAME); dto.setAllowForgotUsername(false);
            UpdateTokenSettingsDto settings=new UpdateTokenSettingsDto();
            settings.setAuthCodeTimeToLive(120); settings.setAccessTokenTimeToLive(600);
            settings.setDeviceCodeTimeToLive(120); settings.setRefreshTokenTimeToLive(3600);
            settings.setReuseRefreshTokens(false); settings.setMaxRequestTransitTime(30);
            var client=applications.create(1,dto,settings,new ForgotUsernameSetting(List.of()),AccessTokenFormatEnum.REFERENCE);
            redirects.createRedirectUris(1,client.getId(),List.of(callback));
            logoutRedirects.createPostLogoutRedirectUris(1,client.getId(),List.of(callback));
            log.info("Created local OAuth client_id={} redirect_uri={}",client.getClientId(),callback);
        } else {
            log.info("Local OAuth client_id={} redirect_uri={}", existing.get().getClientId(), callback);
        }
    }
}
