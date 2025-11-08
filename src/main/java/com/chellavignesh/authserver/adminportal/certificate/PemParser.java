package com.chellavignesh.authserver.adminportal.certificate;

import com.chellavignesh.authserver.adminportal.certificate.exception.InvalidPemException;
import com.chellavignesh.authserver.adminportal.certificate.exception.UnableToParseEncryptedPrivateKeyException;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.chellavignesh.authserver.adminportal.certificate.BouncyCastleConfig.BOUNCY_CASTLE_PROVIDER;

@Service
public class PemParser {
    public PemParseResults parse(InputStream pemFile, String password) throws InvalidPemException {
        try (var parser = new PEMParser(new InputStreamReader(pemFile))) {
            List<Certificate> certificateList = new ArrayList<>();
            PrivateKey privateKey = null;
            Object object;
            while (!Objects.isNull(object = parser.readObject())) {
                switch (object) {
                    case X509CertificateHolder x509CertificateHolder ->
                            certificateList.add(handleX509Certificate(x509CertificateHolder));
                    case PKCS8EncryptedPrivateKeyInfo pkcs8EncryptedPrivateKeyInfo ->
                            privateKey = handlePKC8EncryptedPrivateKeyInfo(pkcs8EncryptedPrivateKeyInfo, password);
                    case PrivateKeyInfo privateKeyInfo -> privateKey = handlePrivateKeyInfo(privateKeyInfo);
                    case PEMEncryptedKeyPair pemEncryptedKeyPair ->
                            privateKey = handleEncryptedKeyPair(pemEncryptedKeyPair, password);
                    default -> throw new InvalidPemException("No objects in pem file match expected types");
                }
            }
            return new PemParseResults(certificateList, privateKey);
        } catch (IOException | CertificateException e) {
            throw new InvalidPemException("Error while parsing PEM file", e);
        } catch (PKCSException e) {
            throw new UnableToParseEncryptedPrivateKeyException("Unable to parse encrypted private key", e);
        }
    }

    private X509Certificate handleX509Certificate(X509CertificateHolder certHolder) throws CertificateException {
        return new JcaX509CertificateConverter().getCertificate(certHolder);
    }

    private PrivateKey handlePKC8EncryptedPrivateKeyInfo(PKCS8EncryptedPrivateKeyInfo keyInfo, String password) throws PKCSException, PEMException, UnableToParseEncryptedPrivateKeyException {
        if (Objects.isNull(keyInfo))
            throw new UnableToParseEncryptedPrivateKeyException("Encrypted private key is null");
        var decryptor = new JcePKCSPBEInputDecryptorProviderBuilder().setProvider(BOUNCY_CASTLE_PROVIDER).build(password.toCharArray());
        return new JcaPEMKeyConverter().getPrivateKey(keyInfo.decryptPrivateKeyInfo(decryptor));
    }

    private PrivateKey handlePrivateKeyInfo(PrivateKeyInfo keyInfo) throws PEMException {
        return new JcaPEMKeyConverter().getPrivateKey(keyInfo);
    }

    private PrivateKey handleEncryptedKeyPair(PEMEncryptedKeyPair pair, String password) throws UnableToParseEncryptedPrivateKeyException {
        try {
            var decryptor = new JcePEMDecryptorProviderBuilder().build(password.toCharArray());
            var decryptedPair = new JcaPEMKeyConverter().getKeyPair(pair.decryptKeyPair(decryptor));
            return decryptedPair.getPrivate();
        } catch (IOException e) {
            throw new UnableToParseEncryptedPrivateKeyException("Failed to read encrypted key pair from PEM file", e);
        }
    }
}
