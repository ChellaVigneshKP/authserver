package com.chellavignesh.authserver.session;

import com.chellavignesh.libcrypto.dto.CryptoRequestResponse;
import com.chellavignesh.libcrypto.dto.Data;
import com.chellavignesh.libcrypto.service.CryptoWebClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Slf4j
public class LibCryptoPasswordEncoder implements PasswordEncoder {
    // TODO: Change this to properly handle from libcrypto

    public static final Integer ENCODER_ID = 0;
    private final CryptoWebClient cryptWebClient;
    private final boolean useLocalFallback;

    public LibCryptoPasswordEncoder(CryptoWebClient cryptWebClient) {
        this.cryptWebClient = cryptWebClient;
        this.useLocalFallback = (cryptWebClient == null);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null) {
            return null;
        }

        String decodedPassword;
        try {
            decodedPassword = new String(Base64.getDecoder().decode(rawPassword.toString()));
        } catch (IllegalArgumentException e) {
            // not base64-encoded, so use raw
            decodedPassword = rawPassword.toString();
        }

        if (useLocalFallback) {
            // simple fallback hashing (e.g., SHA-256)
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(decodedPassword.getBytes());
                return "{" + ENCODER_ID + "}" + Base64.getEncoder().encodeToString(hash);
            } catch (Exception e) {
                throw new RuntimeException("Failed to hash password", e);
            }
        }

        // future real implementation using crypto service
        return "{" + ENCODER_ID + "}" + decodedPassword;
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null || rawPassword == null) {
            return false;
        }

        String decodedPassword;
        try {
            decodedPassword = new String(Base64.getDecoder().decode(rawPassword.toString()));
        } catch (IllegalArgumentException e) {
            decodedPassword = rawPassword.toString();
        }

        if (useLocalFallback) {
            // local SHA-256 compare
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(decodedPassword.getBytes());
                String localEncoded = "{" + ENCODER_ID + "}" + Base64.getEncoder().encodeToString(hash);
                return encodedPassword.equals(localEncoded);
            } catch (Exception e) {
                log.error("Failed to verify password locally", e);
                return false;
            }
        }

        // future secure path — call crypto service
        try {
            var parsed = LibCryptoEncodedPassword.from(encodedPassword);
            Data decryptData = new Data();
            decryptData.setObjectName("$.user");
            decryptData.setPlainText(decodedPassword);
            decryptData.setCipherIndex(parsed.cipherIndex());
            decryptData.setReversible(false);
            CryptoRequestResponse req = new CryptoRequestResponse();
            req.setEncryptData(List.of(decryptData));

            Data resp = cryptWebClient.postCryptoRequestResponse(req)
                    .orElseThrow()
                    .getEncryptData()
                    .get(0);

            var secureAuthEncoded = new LibCryptoEncodedPassword(
                    resp.getCipherIndex(),
                    HexFormat.of().parseHex(resp.getEncryptedCipher())
            );
            return Arrays.equals(parsed.encodedPassword(), secureAuthEncoded.encodedPassword());
        } catch (URISyntaxException e) {
            log.error("Failed to call crypto service", e);
            return false;
        } catch (Exception e) {
            log.error("Error matching password", e);
            return false;
        }
    }

    public record LibCryptoEncodedPassword(int cipherIndex, byte[] encodedPassword) {
        public static LibCryptoEncodedPassword from(String encodedPasswordString) {
            try {
                var encodedByteArray = HexFormat.of().parseHex(encodedPasswordString.replace("0x", ""));
                var cipherIndex = new byte[4];
                var encodedPassword = new byte[encodedByteArray.length - 4];
                System.arraycopy(encodedByteArray, 0, cipherIndex, 0, 4);
                System.arraycopy(encodedByteArray, 4, encodedPassword, 0, encodedPassword.length);
                return new LibCryptoEncodedPassword(ByteBuffer.wrap(cipherIndex).getInt(), encodedPassword);
            } catch (Exception e) {
                // fallback: treat encoded string as plain
                return new LibCryptoEncodedPassword(0, encodedPasswordString.getBytes());
            }
        }
    }
}