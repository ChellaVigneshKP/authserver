package com.chellavignesh.authserver.mfa.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.lang.NonNull;

@Getter
@Setter
@RequiredArgsConstructor
public class OtpValidationRequestDto {
    @NonNull
    private String user_id;
    @NonNull
    private String otp;
}
