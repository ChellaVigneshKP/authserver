package com.chellavignesh.authserver.adminportal.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateUserNotificationConfig {

    private boolean failNotificationSilently = false;
    private boolean alwaysSendNotification = false;

    public static CreateUserNotificationConfig getDefault() {
        return new CreateUserNotificationConfig();
    }
}
