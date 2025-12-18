package com.chellavignesh.authserver.session;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotificationEmail {

    private String from;
    private String fromName;
    private String to;
    private String cc;
    private String replyTo;
    private String subject;
    private String body;
    private String priority;
    private Metadata metadata;

    @Getter
    @AllArgsConstructor
    public static class Metadata {
        private String branding;
        private String emailName;
        private String loginId;
        private String applicationId;
        private String largeEmail;
        private String emailSendDate;
        private String type;
    }
}
