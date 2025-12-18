package com.chellavignesh.authserver.adminportal.user;

import com.chellavignesh.authserver.adminportal.user.entity.ProfileOrganization;
import com.chellavignesh.authserver.adminportal.user.entity.User;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ProfileOrganizationService {

    private final UserRepository userRepository;

    @Autowired
    public ProfileOrganizationService(
            @NotNull final UserRepository userRepository
    ) {
        this.userRepository = userRepository;
    }

    public Optional<ProfileOrganization> getProfileOrganizationFromUser(
            @NotNull final User user
    ) {
        return userRepository.getProfileOrganizationByProfileId(
                user.getId()
        );
    }
}

