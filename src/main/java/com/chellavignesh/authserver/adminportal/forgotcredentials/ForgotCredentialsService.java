package com.chellavignesh.authserver.adminportal.forgotcredentials;

import com.chellavignesh.authserver.adminportal.application.ApplicationRepository;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.application.entity.ApplicationDetail;
import com.chellavignesh.authserver.adminportal.application.exception.AppNotFoundException;
import com.chellavignesh.authserver.adminportal.externalsource.exception.InvalidBrandingException;
import com.chellavignesh.authserver.adminportal.forgotcredentials.exceptions.FailedToSendEmailException;
import com.chellavignesh.authserver.adminportal.metadata.dto.OutgoingMetadataDto;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.dto.BasicUpdateUserPasswordDto;
import com.chellavignesh.authserver.adminportal.user.dto.NotifiableUpdateUserPasswordDto;
import com.chellavignesh.authserver.adminportal.user.dto.ResetUserPasswordDto;
import com.chellavignesh.authserver.adminportal.user.dto.UpdateIntent;
import com.chellavignesh.authserver.adminportal.user.entity.User;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.chellavignesh.authserver.adminportal.user.exception.UserNotFoundException;
import com.chellavignesh.authserver.adminportal.user.exception.UserUpdateFailedException;
import com.chellavignesh.authserver.session.AuthSessionService;
import com.chellavignesh.authserver.session.NotificationEmail;
import com.chellavignesh.authserver.session.NotificationEmailServiceClient;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.session.exception.AuthSessionNotFoundException;
import com.chellavignesh.authserver.session.exception.InvalidSessionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class ForgotCredentialsService {

    private final ApplicationRepository applicationRepository;
    private final UserService userService;
    private final AuthSessionService authSessionService;
    private final NotificationEmailServiceClient notificationEmailServiceClient;

    @Autowired
    public ForgotCredentialsService(ApplicationRepository applicationRepository, UserService userService, AuthSessionService authSessionService, NotificationEmailServiceClient notificationEmailServiceClient) {

        this.applicationRepository = applicationRepository;
        this.userService = userService;
        this.authSessionService = authSessionService;
        this.notificationEmailServiceClient = notificationEmailServiceClient;
    }

    public Optional<Application> checkClientId(String clientId) throws AppNotFoundException {
        var application = applicationRepository.getByClientId(clientId);

        if (application.isEmpty()) {
            log.error("Failed to locate registered application for client ID: {}", clientId);
            throw new AppNotFoundException("Failed to locate registered application for client ID: " + clientId);
        }

        return application;
    }

    public Optional<ApplicationDetail> getApplicationDetailsByClientId(String clientId) throws AppNotFoundException {

        var application = applicationRepository.getByClientId(clientId);

        if (application.isEmpty()) {
            log.error("Failed to locate registered application for client ID: {}", clientId);
            throw new AppNotFoundException("Failed to locate registered application for client ID: " + clientId);
        }

        return applicationRepository.getApplicationDetailById(application.get().getId());
    }

    public UserDetails getUser(UUID sessionId, String clientId, String branding) throws AuthSessionNotFoundException, InvalidSessionException, AppNotFoundException, UserNotFoundException {

        Optional<AuthSession> optionalAuthSession = authSessionService.getBySessionId(sessionId);

        if (optionalAuthSession.isEmpty()) {
            throw new AuthSessionNotFoundException("Unable to locate auth session to reset password");
        }

        AuthSession authSession = optionalAuthSession.get();

        if (!authSession.getScopes().contains("forgot-password")) {
            throw new InvalidSessionException("Session does not have forgot password scope");
        }

        if (authSession.getApplicationId() == null || applicationRepository.getByClientId(clientId).isEmpty()) {
            throw new AppNotFoundException("Failed to locate application for client: %s".formatted(clientId));
        }

        return userService.getByUsernameAndBranding(authSession.getSubjectId(), branding).orElseThrow(() -> new UserNotFoundException("Failed to find user"));
    }

    public User resetUserPassword(ResetUserPasswordDto resetUserPasswordDto, UUID userGuid, String branding) throws UserUpdateFailedException, InvalidBrandingException {

        final var updateUserPasswordDto = new BasicUpdateUserPasswordDto();
        updateUserPasswordDto.setPassword(resetUserPasswordDto.getPassword());
        updateUserPasswordDto.setBranding(branding);

        return userService.updatePassword(updateUserPasswordDto, userGuid, true);
    }

    public User resetUserPasswordAndNotify(ResetUserPasswordDto resetUserPasswordDto, UUID userGuid, String branding, Map<String, String> sourceHeaders) throws UserUpdateFailedException, InvalidBrandingException {

        final var updateUserPasswordDto = new NotifiableUpdateUserPasswordDto();
        updateUserPasswordDto.setPassword(resetUserPasswordDto.getPassword());
        updateUserPasswordDto.setBranding(branding);
        updateUserPasswordDto.setIntent(UpdateIntent.IDP);
        updateUserPasswordDto.setMetadata(OutgoingMetadataDto.createEmpty());

        return userService.updatePasswordAndNotify(updateUserPasswordDto, userGuid, true, sourceHeaders);
    }

    public void emailUsername(NotificationEmail notificationEmail) throws FailedToSendEmailException {

        if (!notificationEmailServiceClient.sendEmailNotification(notificationEmail)) {
            throw new FailedToSendEmailException("Failed to send email notification");
        }
    }
}
