package com.chellavignesh.authserver.mfa.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.lang.NonNull;

import java.util.UUID;

@Getter
@Setter
@RequiredArgsConstructor
public class OtpReceiverDto {
    @NonNull
    private String user_id;
    @NonNull
    private String type;
    @NonNull
    private String factor_id;
    final boolean evaluate_number = false;

    public static OtpReceiverDto forSessionId(UUID sessionId) {
        return new OtpReceiverDto(sessionId.toString(), "sms", "Phone1");
    }
}
