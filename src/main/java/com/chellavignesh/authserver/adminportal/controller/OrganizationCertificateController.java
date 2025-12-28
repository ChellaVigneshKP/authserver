package com.chellavignesh.authserver.adminportal.controller;

import com.chellavignesh.authserver.adminportal.certificate.CertificateEntity;
import com.chellavignesh.authserver.adminportal.certificate.OrganizationCertificateService;
import com.chellavignesh.authserver.adminportal.certificate.dto.CertificateResponseDto;
import com.chellavignesh.authserver.adminportal.certificate.dto.CreateCertificateRequest;
import com.chellavignesh.authserver.adminportal.certificate.dto.CreateCertificateResponse;
import com.chellavignesh.authserver.adminportal.certificate.exception.CertificateNotFoundException;
import com.chellavignesh.authserver.adminportal.certificate.exception.FailedToCreateFingerprintException;
import com.chellavignesh.authserver.adminportal.certificate.exception.FailedToStoreCertificateException;
import com.chellavignesh.authserver.adminportal.certificate.exception.InvalidFileException;
import com.chellavignesh.authserver.adminportal.organization.exception.OrgNotFoundException;
import com.chellavignesh.authserver.adminportal.util.EntityValidator;
import com.chellavignesh.authserver.cms.BrandUrlMappingService;
import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.enums.entity.CertificateType;
import com.chellavignesh.authserver.keystore.exception.FailedToCreateKeyStorePairException;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/certificates")
public class OrganizationCertificateController {

    private final OrganizationCertificateService organizationCertificateService;
    private final EntityValidator entityValidator;
    private final BrandUrlMappingService brandUrlMappingService;

    public OrganizationCertificateController(OrganizationCertificateService organizationCertificateService, EntityValidator entityValidator, BrandUrlMappingService brandUrlMappingService) {
        this.organizationCertificateService = organizationCertificateService;
        this.entityValidator = entityValidator;
        this.brandUrlMappingService = brandUrlMappingService;
    }

    @PostMapping
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) and ((#request.type == 'ORGANIZATION' and hasPermission('idp-admin-org-cert','create')) or (#request.type == 'PUBLIC_KEY' and hasPermission('idp-admin-cred-cert','create')))")
    public ResponseEntity<CreateCertificateResponse> createCertificate(@PathVariable("orgId") UUID orgGuid, @Valid CreateCertificateRequest request, @SessionAttribute(name = ApplicationConstants.BRANDING_INFO, required = false) String brand) throws URISyntaxException, InvalidFileException, FailedToStoreCertificateException, FailedToCreateKeyStorePairException, FailedToCreateFingerprintException {

        try (var certificateFile = request.certificate().getInputStream()) {

            var certId = organizationCertificateService.createCertificate(orgGuid, request.name(), request.password(), CertificateType.valueOf(request.type()), certificateFile);

            return ResponseEntity.created(new URI(brandUrlMappingService.getUrlByBrand(brand) + "/api/v1/organizations/%s/certificates/%s".formatted(orgGuid.toString(), certId.toString()))).body(new CreateCertificateResponse(certId));

        } catch (IOException e) {
            throw new InvalidFileException("Could not load file", e);
        }
    }

    @GetMapping
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) and (hasPermission('idp-admin-org-cert','read') or hasPermission('idp-admin-cred-cert','read'))")
    public ResponseEntity<List<CertificateResponseDto>> getCertificates(@Valid @PathVariable("orgId") UUID orgGuid) throws OrgNotFoundException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);

        return ResponseEntity.ok(organizationCertificateService.getAllByOrgId(orgId).stream().map(CertificateResponseDto::fromCertificateEntityReduce).collect(Collectors.toList()));
    }

    @GetMapping("/{certId}")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) and (hasPermission('idp-admin-org-cert','read') or hasPermission('idp-admin-cred-cert','read'))")
    public ResponseEntity<CertificateResponseDto> getCertificate(@Valid @PathVariable("orgId") UUID orgGuid, @Valid @PathVariable("certId") UUID certId) throws OrgNotFoundException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);

        return ResponseEntity.ok(CertificateResponseDto.fromCertificateEntityExtended(organizationCertificateService.get(orgId, certId).get()));
    }

    @GetMapping(value = "/{certId}/download", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) and (hasPermission('idp-admin-org-cert','read') or hasPermission('idp-admin-cred-cert','read'))")
    public ResponseEntity<byte[]> downloadCertificate(@Valid @PathVariable("orgId") UUID orgGuid, @Valid @PathVariable("certId") UUID certId) throws OrgNotFoundException, CertificateNotFoundException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);
        entityValidator.validateCertificateId(orgId, certId);

        CertificateEntity certificate = organizationCertificateService.get(orgId, certId).orElseThrow();

        byte[] outFile = organizationCertificateService.getCertificateInFile(certificate);
        String filename = organizationCertificateService.getNameForFile(certificate);

        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename).body(outFile);
    }

    @DeleteMapping("/{certId}")
    @PreAuthorize("@permissionsService.isOrgMember(authentication, #orgGuid) and (hasPermission('idp-admin-org-cert','delete') or hasPermission('idp-admin-cred-cert','delete'))")
    public ResponseEntity<?> deleteCertificate(@Valid @PathVariable("orgId") UUID orgGuid, @PathVariable @Valid UUID certId) throws OrgNotFoundException, CertificateNotFoundException {

        Integer orgId = entityValidator.validateOrganizationId(orgGuid);

        boolean wasDeleted = organizationCertificateService.deleteCertificate(orgId, certId);

        if (!wasDeleted) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Certificate not found");
        }

        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<String> handleInvalidFileException(Exception e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}

