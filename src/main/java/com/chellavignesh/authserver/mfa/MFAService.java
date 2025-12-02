package com.chellavignesh.authserver.mfa;

import com.chellavignesh.authserver.mfa.dto.*;
import com.chellavignesh.authserver.mfa.exception.*;
import com.chellavignesh.authserver.mfa.mfarealm.MFARealmCache;
import com.chellavignesh.authserver.mfa.mfarealm.entity.MFARealm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class MFAService {
    private static final String FACTOR_ENDPOINT = "/api/v2/users/%s/factors";
    private static final String REQUEST_OTP_ENDPOINT = "/api/v2/auth";
    private static final String VALIDATE_OTP_ENDPOINT = "/api/v1/otp/validate";

    private final SecureAuthClient secureAuthClient;
    private final MFARealmCache mfaRealmCache;

    @Autowired
    public MFAService(SecureAuthClient secureAuthClient, MFARealmCache mfaRealmCache) {
        this.secureAuthClient = secureAuthClient;
        this.mfaRealmCache = mfaRealmCache;
    }

    public void generateAndSendOTPCode(OtpReceiverDto receiverInfo, Integer realmId) throws OtpGenerationFailedException {
        try {
            this.sendOTP(receiverInfo, realmId);
        } catch (OtpGenerationFailedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OtpGenerationFailedException("Error while sending OTP", ex);
        }
    }

    OtpResponseDto sendOTP(OtpReceiverDto receiverInfo, Integer realmId) throws OtpGenerationFailedException {
        try {
            MFARealm mfaRealm = getMfaRealmFromCache(realmId);
            OtpResponseDto responseDto = secureAuthClient.callSecureAuth(
                    realmId,
                    mfaRealm.getUri(),
                    mfaRealm.getName(),
                    REQUEST_OTP_ENDPOINT,
                    HttpMethod.POST,
                    receiverInfo,
                    OtpResponseDto.class
            );

            if (!responseDto.getStatus().equals("valid")) {
                throw new OtpGenerationFailedException("Secure auth server returned an invalid response while generating OTP: " + responseDto.getMessage());
            }

            return responseDto;
        } catch (SecureAuthException | MFARealmNotFoundException ex) {
            throw new OtpGenerationFailedException("Failed to request OTP from Secure Auth", ex);
        }
    }

    public List<MFAFactor> getUserFactors(String username, Integer realmId) throws FactorRetrievalFailedException {
        String path = FACTOR_ENDPOINT.formatted(username);

        try {
            MFARealm mfaRealm = getMfaRealmFromCache(realmId);
            MFAFactorResponseDto response = secureAuthClient.callSecureAuth(
                    realmId,
                    mfaRealm.getUri(),
                    mfaRealm.getName(),
                    path,
                    HttpMethod.GET,
                    null,
                    MFAFactorResponseDto.class
            );

            if (response.getStatus().equals("found")) {
                if (response.getFactors().isEmpty()) {
                    throw new FactorRetrievalFailedException("User has no MFA factors available");
                }
                return response.getFactors();
            }
            throw new FactorRetrievalFailedException("Status: %s, Message: %s".formatted(response.getStatus(), response.getMessage()));
        } catch (SecureAuthException | MFARealmNotFoundException ex) {
            throw new FactorRetrievalFailedException("Failed to get factors for session %s".formatted(username), ex);
        }
    }

    public boolean validateMFACode(OtpValidationRequestDto requestDto, Integer realmId) throws SecureAuthException, InvalidMFACodeException, MFARealmNotFoundException {

        MFARealm mfaRealm = getMfaRealmFromCache(realmId);
        OtpResponseDto responseDto = secureAuthClient.callSecureAuth(
                realmId,
                mfaRealm.getUri(),
                mfaRealm.getName(),
                VALIDATE_OTP_ENDPOINT,
                HttpMethod.POST,
                requestDto,
                OtpResponseDto.class
        );

        if (!responseDto.getStatus().equals("valid")) {
            throw new InvalidMFACodeException("User entered an invalid MFA code");
        }

        return true;
    }

    private MFARealm getMfaRealmFromCache(Integer realmId) throws MFARealmNotFoundException {
        MFARealm mfaRealm = mfaRealmCache.getMfaRealm(realmId);
        if (mfaRealm == null) {
            log.error("MFA Realm id {} not found.", realmId);
            throw new MFARealmNotFoundException("MFA realm not found.");
        }
        return mfaRealm;
    }
}
