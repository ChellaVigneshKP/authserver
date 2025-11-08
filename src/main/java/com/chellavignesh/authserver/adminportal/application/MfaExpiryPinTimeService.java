package com.chellavignesh.authserver.adminportal.application;

import com.chellavignesh.authserver.adminportal.application.entity.MfaExpiry;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MfaExpiryPinTimeService {
    private final MfaExpiryPinTimeRepository mfaExpiryPinTimeRepository;

    public MfaExpiryPinTimeService(MfaExpiryPinTimeRepository mfaExpiryPinTimeRepository) {
        this.mfaExpiryPinTimeRepository = mfaExpiryPinTimeRepository;
    }

    public MfaExpiry getMfaExpiryPinTime(Integer pinTimeToLive, UUID sessionId) {
        return mfaExpiryPinTimeRepository.getMfaExpiryPinTime(pinTimeToLive, sessionId);
    }
}
