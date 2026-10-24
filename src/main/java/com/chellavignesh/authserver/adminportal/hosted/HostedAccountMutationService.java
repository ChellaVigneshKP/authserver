package com.chellavignesh.authserver.adminportal.hosted;

import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.dto.*;
import com.chellavignesh.authserver.adminportal.user.dto.validator.*;
import com.chellavignesh.authserver.adminportal.util.PhoneNumberUtil;
import com.chellavignesh.authserver.security.ManagementMutationLock;
import com.chellavignesh.authserver.session.AuthSessionService;
import com.chellavignesh.authserver.session.CredentialAuthenticationLock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;

@Service
public class HostedAccountMutationService {
    private final HostedAccountAccessPolicy policy;
    private final AuthSessionService sessions;
    private final UserService users;
    private final ManagementMutationLock management;
    private final CredentialAuthenticationLock credentials;
    private final ChangeProfileDtoValidator profiles;
    private final ChangeProfileViewDtoValidator views;
    private final ChangePasswordDtoValidator passwordValidator;
    private final com.chellavignesh.authserver.session.PasswordEncoderFactory encoder;
    private final HostedAccountPasswordRepository passwords;
    private final HostedAccountRevocationRepository revocations;
    private final com.chellavignesh.authserver.adminportal.globalconfig.GlobalConfigCache config;

    public HostedAccountMutationService(
            HostedAccountAccessPolicy policy,
            AuthSessionService sessions,
            UserService users,
            ManagementMutationLock management,
            CredentialAuthenticationLock credentials,
            ChangeProfileDtoValidator profiles,
            ChangeProfileViewDtoValidator views,
            ChangePasswordDtoValidator passwordValidator,
            com.chellavignesh.authserver.session.PasswordEncoderFactory encoder,
            HostedAccountPasswordRepository passwords,
            HostedAccountRevocationRepository revocations,
            com.chellavignesh.authserver.adminportal.globalconfig.GlobalConfigCache config) {
        this.policy = policy;
        this.sessions = sessions;
        this.users = users;
        this.management = management;
        this.credentials = credentials;
        this.profiles = profiles;
        this.views = views;
        this.passwordValidator = passwordValidator;
        this.encoder = encoder;
        this.passwords = passwords;
        this.revocations = revocations;
        this.config = config;
    }

    private HostedAccountActor fresh(HostedAccountActor actor) {
        management.acquire();
        credentials.acquire(actor.loginId());
        return policy.revalidate(
                actor,
                sessions.getBySessionId(actor.authSession().getSessionId())
                        .orElseThrow(HostedAccountFailure::denied));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(HostedAccountActor actor, ChangeProfileDto dto) throws Exception {
        actor = fresh(actor);
        var errors = new BeanPropertyBindingResult(dto, "profile");
        profiles.validate(dto, errors);
        var view = new ChangeProfileViewDto();
        view.setBranding(actor.branding());
        view.setRedirectUri(actor.callback().toString());
        var viewErrors = new BeanPropertyBindingResult(view, "view");
        views.validate(view, viewErrors);
        if (errors.hasErrors()
                || viewErrors.hasErrors()
                || dto.getFirstName() == null
                || dto.getFirstName().isBlank()
                || dto.getFirstName().length() > 255
                || dto.getLastName() == null
                || dto.getLastName().isBlank()
                || dto.getLastName().length() > 255)
            throw new HostedAccountFailure(
                    400,
                    "Check your profile information. Open the form again to make a correction.");
        dto.setPhoneNumber(PhoneNumberUtil.transformPhoneNumberToE164(dto.getPhoneNumber()));
        dto.setSecondaryPhoneNumber(
                PhoneNumberUtil.transformPhoneNumberToE164(dto.getSecondaryPhoneNumber()));
        users.updateForChangeProfilePage(dto, actor.profileId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePasswordAndRevoke(HostedAccountActor actor, ChangePasswordDto dto)
            throws Exception {
        actor = fresh(actor);
        var errors = new BeanPropertyBindingResult(dto, "password");
        passwordValidator.validate(dto, errors);
        if (errors.hasErrors()) throw passwordInvalid();
        var credential = passwords.current(actor);
        if (!encoder.matches(
                dto.getCurrentPassword(),
                java.util.HexFormat.of().formatHex(credential.password()),
                credential.version())) throw passwordInvalid();
        String next =
                new String(
                        java.util.Base64.getDecoder().decode(dto.getNewPassword()),
                        java.nio.charset.StandardCharsets.UTF_8);
        try {
            com.chellavignesh.authserver.security.PasswordValidatorService.validSemantic(
                    next,
                    actor.user().username(),
                    actor.user().firstName(),
                    actor.user().lastName(),
                    actor.user().email());
        } catch (
                com.chellavignesh.authserver.security.exception.PasswordValidationException
                        invalid) {
            throw passwordInvalid();
        }
        for (var historic : passwords.history(actor))
            if (encoder.matches(
                    dto.getNewPassword(),
                    java.util.HexFormat.of().formatHex(historic.password()),
                    historic.version())) throw passwordInvalid();
        Integer limit =
                config.getGlobalConfig(
                        com.chellavignesh.authserver.enums.entity.GlobalConfigTypeEnum
                                .DISALLOWED_RECENT_PASSWORD_COUNT);
        if (limit == null) throw HostedAccountFailure.unavailable();
        byte[] encoded =
                java.util.HexFormat.of()
                        .parseHex(
                                encoder.encode(
                                        java.util.Base64.getEncoder()
                                                .encodeToString(
                                                        next.getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8))));
        passwords.update(
                actor,
                encoded,
                com.chellavignesh.authserver.session.PasswordEncoderFactory.currentVersion,
                limit);
        revocations.revokeAllForLogin(actor.loginId());
    }

    private static HostedAccountFailure passwordInvalid() {
        return new HostedAccountFailure(
                400,
                "The current password, confirmation or password requirements could not be verified."
                    + " Open the form again to try a different password.");
    }
}
