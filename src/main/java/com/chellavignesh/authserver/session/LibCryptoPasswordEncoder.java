package com.chellavignesh.authserver.session;

import com.chellavignesh.libcrypto.dto.CryptoRequestResponse;
import com.chellavignesh.libcrypto.dto.Data;
import com.chellavignesh.libcrypto.service.CryptoWebClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Slf4j
public class LibCryptoPasswordEncoder implements PasswordEncoder {

    public static final Integer ENCODER_ID = 0;
    private final CryptoWebClient cryptWebClient;
    private final boolean useLocalFallback;

    public LibCryptoPasswordEncoder(CryptoWebClient cryptWebClient) {
        this.cryptWebClient = cryptWebClient;
        this.useLocalFallback = (cryptWebClient == null);
    }

    //----------------------------------------------------------------------
    // ENCODE
    //----------------------------------------------------------------------
    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null) return null;

        String decodedPassword = decodePassword(rawPassword);

        if (useLocalFallback) {
            return encodeLocal(decodedPassword);
        }

        try {
            // build crypto request
            Data data = new Data();
            data.setObjectName("$.user");
            data.setPlainText(decodedPassword);
            data.setReversible(false);

            CryptoRequestResponse req = new CryptoRequestResponse();
            req.setEncryptData(List.of(data));

            // call remote crypto service
            Data resp = cryptWebClient.postCryptoRequestResponse(req)
                    .orElseThrow(() -> new RuntimeException("Empty crypto response"))
                    .getEncryptData()
                    .get(0);

            // build final stored format
            byte[] cipherIndexBytes = ByteBuffer.allocate(4).putInt(resp.getCipherIndex()).array();
            byte[] encryptedBytes = HexFormat.of().parseHex(resp.getEncryptedCipher());

            byte[] combined = new byte[cipherIndexBytes.length + encryptedBytes.length];
            System.arraycopy(cipherIndexBytes, 0, combined, 0, cipherIndexBytes.length);
            System.arraycopy(encryptedBytes, 0, combined, cipherIndexBytes.length, encryptedBytes.length);

            return "{" + ENCODER_ID + "}0x" + HexFormat.of().formatHex(combined);
        } catch (Exception e) {
            log.error("Crypto encode failed. Falling back to local hashing.", e);
            return encodeLocal(decodedPassword);
        }
    }

    //----------------------------------------------------------------------
    // MATCHES
    //----------------------------------------------------------------------
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) return false;

        String decodedPassword = decodePassword(rawPassword);

        if (useLocalFallback) {
            return matchesLocal(decodedPassword, encodedPassword);
        }

        try {
            LibCryptoEncodedPassword parsed = LibCryptoEncodedPassword.from(encodedPassword);

            // build request
            Data data = new Data();
            data.setObjectName("$.user");
            data.setPlainText(decodedPassword);
            data.setCipherIndex(parsed.cipherIndex());
            data.setReversible(false);

            CryptoRequestResponse req = new CryptoRequestResponse();
            req.setEncryptData(List.of(data));

            Data resp = cryptWebClient.postCryptoRequestResponse(req)
                    .orElseThrow()
                    .getEncryptData()
                    .get(0);

            // recreate expected encrypted cipher
            LibCryptoEncodedPassword recreated =
                    new LibCryptoEncodedPassword(resp.getCipherIndex(),
                            HexFormat.of().parseHex(resp.getEncryptedCipher()));

            return Arrays.equals(parsed.encodedPassword(), recreated.encodedPassword());
        } catch (Exception e) {
            log.error("Crypto password match failed", e);
            return false;
        }
    }

    //----------------------------------------------------------------------
    // HELPERS
    //----------------------------------------------------------------------
    private String decodePassword(CharSequence raw) {
        try {
            return new String(Base64.getDecoder().decode(raw.toString()));
        } catch (Exception _) {
            return raw.toString();
        }
    }

    private String encodeLocal(String decodedPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(decodedPassword.getBytes());
            return "{" + ENCODER_ID + "}" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed local password hashing", e);
        }
    }

    private boolean matchesLocal(String decoded, String encodedPassword) {
        return encodeLocal(decoded).equals(encodedPassword);
    }

    //----------------------------------------------------------------------
    // INTERNAL REPRESENTATION
    //----------------------------------------------------------------------
    public record LibCryptoEncodedPassword(int cipherIndex, byte[] encodedPassword) {
        public static LibCryptoEncodedPassword from(String encoded) {
            try {
                String hex = encoded.substring(encoded.indexOf("}") + 1).replace("0x", "");
                byte[] all = HexFormat.of().parseHex(hex);

                byte[] idx = Arrays.copyOfRange(all, 0, 4);
                byte[] pwd = Arrays.copyOfRange(all, 4, all.length);

                return new LibCryptoEncodedPassword(ByteBuffer.wrap(idx).getInt(), pwd);
            } catch (Exception e) {
                return new LibCryptoEncodedPassword(0, encoded.getBytes());
            }
        }
    }
}