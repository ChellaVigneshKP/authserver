package com.chellavignesh.authserver.authcode;

import com.chellavignesh.authserver.authcode.dto.CreateAuthCodeDto;
import com.chellavignesh.authserver.authcode.entity.AuthCode;
import com.chellavignesh.authserver.authcode.exception.AuthCodeCreationFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthCodeService {
    private final AuthCodeRepository authCodeRepository;

    @Autowired
    public AuthCodeService(AuthCodeRepository authCodeRepository) {
        this.authCodeRepository = authCodeRepository;
    }

    public AuthCode create(CreateAuthCodeDto dto) throws AuthCodeCreationFailedException {
        return authCodeRepository.create(dto);
    }

    public Optional<UUID> getSessionIdByAuthCode(String authCode) {
        return authCodeRepository.getSessionIdByAuthCode(authCode);
    }

    public void setConsumedOn(String authCode) {
        authCodeRepository.setConsumedOn(authCode);
    }
}
