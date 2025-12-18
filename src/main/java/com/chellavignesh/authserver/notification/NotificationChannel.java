package com.chellavignesh.authserver.notification;

import org.jetbrains.annotations.NotNull;
import org.springframework.lang.NonNull;

import java.time.ZonedDateTime;
import java.util.Map;

public interface NotificationChannel {

    void notifyEmailChange(
            @NonNull NotificationContext context,
            @NonNull String email
    );

    void notifyUsernameChange(
            @NonNull NotificationContext context,
            @NonNull String username
    );

    void notifyPasswordChange(
            @NonNull NotificationContext context,
            @NonNull String password
    );

    void notifyProfileCreated(
            @NonNull NotificationContext context,
            @NonNull ZonedDateTime updateTimestamp
    );

    void notifyProfileUpdate(
            @NotNull NotificationContext context,
            @NotNull Map<String, Object> alteredFields
    );

    default String getName() {
        return getClass().getName();
    }
}
