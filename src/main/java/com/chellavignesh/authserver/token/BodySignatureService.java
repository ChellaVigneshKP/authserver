package com.chellavignesh.authserver.token;

import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import com.chellavignesh.authserver.token.exception.SignatureFailedException;
import org.jetbrains.annotations.NonNls;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Service
@Lazy
public class BodySignatureService {

    private final TokenService tokenService;

    public BodySignatureService(@Lazy TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * For provided non-null token attempts to obtain signing key and sign provided body
     *
     * @param token not null token
     * @param body  request or response body
     * @return signature for supplied body or throws exception
     * @throws SignatureFailedException when it is not possible to resolve the algorithm or there is something wrong with key
     */
    public byte[] signBody(@NonNls final String token, @NonNls final byte[] body) throws SignatureFailedException {

        var tokenOpt = this.tokenService.getByValue(token, TokenTypeEnum.ACCESS_TOKEN);

        if (tokenOpt.isPresent()) {
            var tokenObj = tokenOpt.get();
            var signingKey = tokenObj.getSigningKey();

            if (signingKey == null) {
                throw new SignatureFailedException("Signing key not available for token ID: " + tokenObj.getId());
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

        return new byte[]{};
    }
}
