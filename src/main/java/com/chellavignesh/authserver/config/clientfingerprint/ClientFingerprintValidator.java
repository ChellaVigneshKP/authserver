package com.chellavignesh.authserver.config.clientfingerprint;

import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.session.fingerprint.ClientFingerprint;
import com.chellavignesh.authserver.session.fingerprint.ClientFingerprintParser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class ClientFingerprintValidator {

    private static final Logger logger = LoggerFactory.getLogger(ClientFingerprintValidator.class);

    private final boolean isFingerprintingRefererDisabled;

    public ClientFingerprintValidator(@Value("${toggles.fingerprinting.referer.disabled}") boolean isFingerprintingRefererDisabled) {
        this.isFingerprintingRefererDisabled = isFingerprintingRefererDisabled;
    }

    public boolean isValidSignature(HttpServletRequest request, byte[] expectedClientFingerprint) {
        try {
            var actualClientFingerprint = new ClientFingerprint(ClientFingerprintParser.parseZoneOffset(request.getHeader(ApplicationConstants.REQUEST_DATETIME_HEADER)), request.getHeader(HttpHeaders.ACCEPT_LANGUAGE), request.getHeader(HttpHeaders.USER_AGENT), getReferer(request)).getBytes();

            return Arrays.equals(expectedClientFingerprint, actualClientFingerprint);
        } catch (Exception e) {
            logger.error("Unable to compare fingerprints, something went wrong: ", e);
            return false;
        }
    }

    public boolean isValidSignature(HttpServletRequest request, byte[] expectedClientFingerprint, String requestDatetime) {
        try {
            var actualClientFingerprint = new ClientFingerprint(ClientFingerprintParser.parseZoneOffset(requestDatetime), request.getHeader(HttpHeaders.ACCEPT_LANGUAGE), request.getHeader(HttpHeaders.USER_AGENT), getReferer(request)).getBytes();

            return Arrays.equals(expectedClientFingerprint, actualClientFingerprint);
        } catch (Exception e) {
            logger.error("Unable to compare fingerprints, something went wrong: ", e);
            return false;
        }
    }

    private String getReferer(HttpServletRequest request) {
        return isFingerprintingRefererDisabled ? null : ClientFingerprintParser.parseRefererHost(request.getHeader(HttpHeaders.REFERER));
    }
}
