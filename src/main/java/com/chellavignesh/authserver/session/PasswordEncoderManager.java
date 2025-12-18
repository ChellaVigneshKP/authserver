package com.chellavignesh.authserver.session;

import com.chellavignesh.authserver.adminportal.externalsource.exception.InvalidBrandingException;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.dto.BasicUpdateUserPasswordDto;
import com.chellavignesh.authserver.adminportal.user.entity.UserAuthDetails;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.chellavignesh.authserver.adminportal.user.exception.UserUpdateFailedException;
import com.chellavignesh.authserver.session.dto.CustomUserDetails;
import com.chellavignesh.authserver.session.exception.FailedToProcessPasswordException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class PasswordEncoderManager {

    private final UserService userService;

    public PasswordEncoderManager(UserService userService) {
        this.userService = userService;
    }

    public void processPassword(Authentication authentication, String password, String branding) throws FailedToProcessPasswordException {

        UserAuthDetails userAuthDetails = ((CustomUserDetails) authentication.getPrincipal()).getUserAuthDetails();

        UserDetails userDetails = userService.getByUsernameAndBranding(userAuthDetails.getUserName(), branding).orElseThrow(() -> new FailedToProcessPasswordException("User details not found for %s, %s pair".formatted(userAuthDetails.getUserName(), branding)));

        if (!PasswordEncoderFactory.currentVersion.equals(userAuthDetails.getVersion())) {
            try {
                userService.updatePassword(new BasicUpdateUserPasswordDto(password, branding), userDetails.rowGuid());
            } catch (UserUpdateFailedException e) {
                throw new FailedToProcessPasswordException("Error updating the user password", e);
            } catch (InvalidBrandingException e) {
                throw new FailedToProcessPasswordException("Invalid brand %s passed to update password".formatted(branding), e);
            }
        }
    }
}
