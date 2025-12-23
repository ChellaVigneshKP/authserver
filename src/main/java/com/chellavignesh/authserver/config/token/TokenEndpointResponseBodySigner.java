package com.chellavignesh.authserver.config.token;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.certificate.CertificateEntity;
import com.chellavignesh.authserver.adminportal.certificate.CertificateStatus;
import com.chellavignesh.authserver.adminportal.certificate.OrganizationCertificateService;
import com.chellavignesh.authserver.enums.entity.CertificateType;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;

@Component
public class TokenEndpointResponseBodySigner {

    private static final Logger logger = LoggerFactory.getLogger(TokenEndpointResponseBodySigner.class);

    private final ApplicationService applicationService;
    private final OrganizationCertificateService organizationCertificateService;

    public TokenEndpointResponseBodySigner(ApplicationService applicationService, OrganizationCertificateService organizationCertificateService) {
        this.applicationService = applicationService;
        this.organizationCertificateService = organizationCertificateService;
    }

    public byte[] signResponseBody(byte[] body, String clientId) {
        var application = this.applicationService.getByClientId(clientId);

        if (application.isPresent()) {
            return signBodyWithLatestOrgCertificateForApplication(body, application.get());
        }

        return new byte[]{};
    }

    private byte[] signBodyWithLatestOrgCertificateForApplication(byte[] body, Application application) {
        var certificateEntities = this.organizationCertificateService.getAllByOrgId(application.getOrgId());

        var certificateWithLatestExpiration = certificateEntities.stream().filter(certificateEntity -> certificateEntity.getType() == CertificateType.ORGANIZATION).filter(certificateEntity -> certificateEntity.getStatus() == CertificateStatus.ACTIVE).max((o1, o2) -> {
            try {
                var jwk1 = JWK.parse(o1.getKeyStorePair().getCertificate().get());
                var jwk2 = JWK.parse(o2.getKeyStorePair().getCertificate().get());
                return jwk1.getExpirationTime().compareTo(jwk2.getExpirationTime());
            } catch (JOSEException e) {
                return o1.getValidTo().compareToIgnoreCase(o2.getValidTo());
            }
        });

        if (certificateWithLatestExpiration.isPresent()) {
            return signBodyWithCertificate(body, certificateWithLatestExpiration.get());
        }

        return new byte[]{};
    }

    private static byte[] signBodyWithCertificate(byte[] body, CertificateEntity certificateEntity) {
        var keyStorePair = certificateEntity.getKeyStorePair();
        var certificate = keyStorePair.getCertificate();

        if (certificate.isPresent()) {
            PublicKey publicKey = certificate.get().getPublicKey();

            String algorithm;

            if (publicKey instanceof RSAPublicKey) {
                algorithm = "SHA256withRSA";
            } else if (publicKey instanceof ECPublicKey) {
                algorithm = "SHA256withECDSAinP1363Format";
            } else {
                logger.error("Unsupported public key algorithm: {}", publicKey.getAlgorithm());
                return new byte[]{};
            }

            Optional<PrivateKey> privateKey = keyStorePair.getPrivateKey();

            if (privateKey.isPresent()) {
                return signBodyWithPrivateKey(body, algorithm, privateKey.get());
            }
        }

        return new byte[]{};
    }

    private static byte[] signBodyWithPrivateKey(byte[] body, String algorithm, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance(algorithm);

            signature.initSign(privateKey);
            signature.update(body);

            return signature.sign();

        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {

            logger.error("Could not sign response body", e);
            return new byte[]{};
        }
    }
}
