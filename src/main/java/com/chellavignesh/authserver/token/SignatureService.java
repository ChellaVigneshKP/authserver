package com.chellavignesh.authserver.token;

import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import com.chellavignesh.authserver.token.entity.Token;
import com.chellavignesh.authserver.token.exception.SignatureFailedException;
import com.chellavignesh.authserver.token.exception.SignatureVerificationFailedException;
import lombok.Getter;
import org.jetbrains.annotations.NonNls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.regex.Pattern;

@Service
public class SignatureService {

    private static final Logger logger = LoggerFactory.getLogger(SignatureService.class);

    @Getter
    private final boolean signatureRequired;

    private final TokenService tokenService;

    public SignatureService(TokenService tokenService, @Value("${toggles.signature.required:false}") boolean signatureRequired) {
        this.tokenService = tokenService;
        this.signatureRequired = signatureRequired;
    }

    /**
     * Verifies the signature included in a request.
     *
     * @param authorizationHeader The header containing the access token
     * @param signatureHeader     The signature header value
     * @param body                Body of the request
     * @return true if signature verifies using token key, otherwise false
     * @throws SignatureVerificationFailedException when signature cannot be verified
     */
    public boolean verifySignature(String authorizationHeader, String signatureHeader, byte[] body) throws SignatureVerificationFailedException {

        try {
            var computedSignature = this.signBody(authorizationHeader, body);
            logger.debug("Computed signature: {}", Base64.getEncoder().encodeToString(computedSignature));
            var signatureFromRequest = Base64.getDecoder().decode(signatureHeader);
            return Arrays.equals(signatureFromRequest, computedSignature);
        } catch (SignatureFailedException e) {
            throw new SignatureVerificationFailedException("Could not verify signature", e);
        }
    }

    /**
     * Resolves signing key from ClientID and verifies signature for provided body.
     */
    public boolean verifySignatureForClientId(final String clientId, final Date requestDateTime, final String signatureValue, final byte[] body) throws SignatureVerificationFailedException {

        final var tokensList = tokenService.getTokensByClientIdAndRequestDateTime(clientId, requestDateTime);

        if (tokensList.isEmpty()) {
            logger.error("Unable to find any valid tokens for ClientID: {} and requestDateTime: {}", clientId, requestDateTime);
            throw new SignatureVerificationFailedException("No valid sessions found, cannot verify signature");
        }

        var signatureValidForAnyOfTokens = false;

        for (var token : tokensList) {
            if (signatureValidForAnyOfTokens) break;

            try {
                var computedSignature = this.signBody(token, body);
                var signatureFromRequest = Base64.getDecoder().decode(signatureValue);
                signatureValidForAnyOfTokens = Arrays.equals(computedSignature, signatureFromRequest);
            } catch (SignatureFailedException e) {
                throw new SignatureVerificationFailedException("Unable to verify signature", e);
            }
        }
        return signatureValidForAnyOfTokens;
    }

    /**
     * Extracts access token from Authorization header and signs the body.
     */
    public byte[] signBody(String authorizationHeader, byte[] body) throws SignatureFailedException {
        var pattern = Pattern.compile("^Bearer\\s+(.*)$");
        var matcher = pattern.matcher(authorizationHeader);
        if (matcher.matches()) {
            var accessToken = matcher.group(1);
            var token = this.tokenService.getByValue(accessToken, TokenTypeEnum.ACCESS_TOKEN);
            if (token.isPresent()) {
                return this.signBody(token.get(), body);
            }
        }

        return new byte[]{};
    }

    /**
     * Signs body using token's signing key.
     */
    public byte[] signBody(@NonNls final Token token, @NonNls final byte[] body) throws SignatureFailedException {
        var signingKey = token.getSigningKey();
        if (signingKey == null) {
            throw new SignatureFailedException("Signing key not available for token ID: " + token.getId());
        }

        var secretKeySpec = new SecretKeySpec(signingKey, "HmacSHA256");

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
            return mac.doFinal(body);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new SignatureFailedException("Could not sign body", e);
        }
    }
}
