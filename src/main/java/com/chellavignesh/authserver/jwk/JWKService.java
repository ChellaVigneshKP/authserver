package com.chellavignesh.authserver.jwk;

import com.chellavignesh.authserver.adminportal.certificate.CertificateEntity;
import com.chellavignesh.authserver.adminportal.certificate.CertificateStatus;
import com.chellavignesh.authserver.adminportal.certificate.OrganizationCertificateService;
import com.chellavignesh.authserver.adminportal.organization.OrganizationService;
import com.chellavignesh.authserver.adminportal.organization.exception.OrgNotFoundException;
import com.chellavignesh.authserver.enums.entity.CertificateType;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class JWKService {
    private final OrganizationCertificateService organizationCertificateService;
    private final OrganizationService organizationService;

    @Autowired
    public JWKService(OrganizationCertificateService organizationCertificateService, OrganizationService organizationService) {
        this.organizationCertificateService = organizationCertificateService;
        this.organizationService = organizationService;
    }

    public List<JWK> getByOrgGuid(UUID orgGuid) throws OrgNotFoundException {
        var orgId = organizationService.get(orgGuid).orElseThrow(() -> new OrgNotFoundException("Organization not found with GUID: " + orgGuid));
        return getJWKs(organizationCertificateService.getAllByOrgId(orgId.getId()).stream().filter((cert) -> cert.getType() == CertificateType.ORGANIZATION && cert.getStatus() == CertificateStatus.ACTIVE).toList());
    }

    public List<JWK> get(String clientId) {
        List<CertificateEntity> publicKeys = organizationCertificateService.getAllByClientIdAndCertTypeId(clientId, CertificateType.PUBLIC_KEY.getValue());
        return getJWKs(publicKeys);
    }

    private List<JWK> getJWKs(List<CertificateEntity> certificateEntities) {
        List<JWK> jwkList = new ArrayList<>();
        for (var entity : certificateEntities) {
            if (entity.getKeyStorePair() != null && entity.getKeyStorePair().getCertificate().isPresent()) {
                try {
                    jwkList.add(JWK.parse(entity.getKeyStorePair().getCertificate().get()));
                } catch (JOSEException _) {

                }
            }
        }
        return jwkList;
    }
}
