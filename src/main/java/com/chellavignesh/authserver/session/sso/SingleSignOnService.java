package com.chellavignesh.authserver.session.sso;

import com.chellavignesh.authserver.config.clientfingerprint.ClientFingerprintValidator;
import com.chellavignesh.authserver.enums.entity.AuthSessionStatusEnum;
import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import com.chellavignesh.authserver.keystore.service.SecretKeyGenerator;
import com.chellavignesh.authserver.session.AuthSessionService;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.chellavignesh.authserver.session.sso.exception.*;
import com.chellavignesh.authserver.token.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.chellavignesh.authserver.keystore.KeyStoreConstants.AES_KEY_GENERATOR;

@Service
@Slf4j
public class SingleSignOnService {

    private final SecretKeyGenerator secretKeyGenerator;
    private final SingleSignOnRepository singleSignOnRepository;
    private final AuthSessionService authSessionService;
    private final ClientFingerprintValidator clientFingerprintValidator;
    private final TokenService tokenService;
    private final boolean fingerprintingEnabled;

    public SingleSignOnService(SecretKeyGenerator secretKeyGenerator, SingleSignOnRepository singleSignOnRepository, AuthSessionService authSessionService, ClientFingerprintValidator clientFingerprintValidator, TokenService tokenService, @Value("${toggles.fingerprinting.enabled}") boolean fingerprintingEnabled) {
        this.secretKeyGenerator = secretKeyGenerator;
        this.singleSignOnRepository = singleSignOnRepository;
        this.authSessionService = authSessionService;
        this.clientFingerprintValidator = clientFingerprintValidator;
        this.tokenService = tokenService;
        this.fingerprintingEnabled = fingerprintingEnabled;
    }

    public SingleSignOnCookie generateCookie(UUID sessionId) throws FailedToGenerateCookieException {
        try {
            var secretKey = secretKeyGenerator.generateKey();
            var singleSignOnCookie = new SingleSignOnCookie(sessionId, encryptSessionId(sessionId, secretKey), secretKey.getEncoded());
            singleSignOnRepository.insertCookie(singleSignOnCookie);
            return singleSignOnCookie;
        } catch (Exception e) {
            throw new FailedToGenerateCookieException(e);
        }
    }

    private byte[] encryptSessionId(UUID sessionId, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_KEY_GENERATOR);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(sessionId.toString().getBytes());
    }

    public Optional<SingleSignOnCookie> getCookie(byte[] encryptedSessionId) {
        return singleSignOnRepository.findCookieByEncryptedSessionId(encryptedSessionId);
    }

    public Optional<OAuth2AccessTokenResponse> getAccessToken(byte[] encryptedSessionId, HttpServletRequest request) {
        try {
            var authSession = getAuthSessionFromCookie(encryptedSessionId).filter(session -> isValidAuthSession(session, request)).orElseThrow(InvalidAuthSessionException::new);

            return Optional.of(createTokenResponse(authSession));
        } catch (InvalidSingleSignOnCookieException | InvalidAuthSessionException | NoActiveAccessTokensException e) {
            return Optional.empty();
        }
    }

    public Optional<AuthSession> getAuthSessionFromCookie(byte[] encryptedSessionId) throws InvalidSingleSignOnCookieException {
        log.debug("Getting AuthSession from SSO cookie");
        var cookie = getCookie(encryptedSessionId).orElseThrow(InvalidSingleSignOnCookieException::new);
        log.debug("SSO cookie found - Session UUID: {}", cookie.sessionId());
        var session = authSessionService.getBySessionId(cookie.sessionId());
        if (session.isPresent()) {
            log.debug("AuthSession found in database for UUID: {}", cookie.sessionId());
        } else {
            log.warn("AuthSession NOT FOUND in database for UUID: {}", cookie.sessionId());
        }

        return session;
    }

    public boolean isValidAuthSession(AuthSession authSession, HttpServletRequest request) {
        if (authSession.getAuthSessionStatus().equals(AuthSessionStatusEnum.INACTIVE)) {
            return false;
        }

        return !fingerprintingEnabled || clientFingerprintValidator.isValidSignature(request, authSession.getClientFingerprint());
    }

    public void validateSessionFingerprintAndThrow(AuthSession authSession, HttpServletRequest request, String requestDatetime) throws FingerprintFailedException, InactiveAuthSessionException {
        if (authSession.getAuthSessionStatus().equals(AuthSessionStatusEnum.INACTIVE)) {
            throw new InactiveAuthSessionException("Session inactive.");
        }

        if (fingerprintingEnabled && !clientFingerprintValidator.isValidSignature(request, authSession.getClientFingerprint(), requestDatetime)) {
            throw new FingerprintFailedException("Fingerprint validation failed.");
        }
    }

    private OAuth2AccessTokenResponse createTokenResponse(AuthSession authSession) throws NoActiveAccessTokensException {

        var accessToken = tokenService.getAllActiveBySessionId(authSession.getSessionId()).stream().filter(token -> Objects.equals(token.getTokenType(), TokenTypeEnum.ACCESS_TOKEN)).findFirst().orElseThrow(NoActiveAccessTokensException::new);

        log.warn("Single Sign On Auth Session Session ID: {}", authSession.getSessionId());

        return OAuth2AccessTokenResponse.withToken(accessToken.getData()).tokenType(OAuth2AccessToken.TokenType.BEARER).scopes(authSession.getScopes()).expiresIn(Duration.between(Instant.now(), accessToken.getExpiration().toInstant()).get(ChronoUnit.SECONDS)).additionalParameters(Map.of("signing_key", accessToken.getSigningKey(), "redirect_uri", authSession.getRedirectUri())).build();
    }
}
