package com.chellavignesh.authserver.adminportal.util;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.TokenSettingsService;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.application.entity.Resource;
import com.chellavignesh.authserver.adminportal.application.entity.TokenSettings;
import com.chellavignesh.authserver.adminportal.application.exception.AppNotFoundException;
import com.chellavignesh.authserver.adminportal.application.exception.AppResourceNotFoundException;
import com.chellavignesh.authserver.adminportal.application.exception.TokenSettingsNotFoundException;
import com.chellavignesh.authserver.adminportal.certificate.CertificateEntity;
import com.chellavignesh.authserver.adminportal.certificate.OrganizationCertificateService;
import com.chellavignesh.authserver.adminportal.certificate.exception.CertificateNotFoundException;
import com.chellavignesh.authserver.adminportal.organization.OrganizationService;
import com.chellavignesh.authserver.adminportal.organization.entity.Organization;
import com.chellavignesh.authserver.adminportal.organization.entity.OrganizationGroup;
import com.chellavignesh.authserver.adminportal.organization.exception.OrgGroupNotFoundException;
import com.chellavignesh.authserver.adminportal.organization.exception.OrgNotFoundException;
import com.chellavignesh.authserver.adminportal.organization.exception.ResourceLibraryNotFoundException;
import com.chellavignesh.authserver.adminportal.resource.ResourceLibraryService;
import com.chellavignesh.authserver.adminportal.resource.entity.ResourceLibrary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class EntityValidator {

    private final OrganizationService organizationService;
    private final OrganizationCertificateService organizationCertificateService;
    private final ApplicationService applicationService;
    private final TokenSettingsService tokenSettingsService;
    private final ResourceLibraryService resourceLibraryService;

    @Autowired
    public EntityValidator(
            OrganizationService organizationService,
            OrganizationCertificateService organizationCertificateService,
            ApplicationService applicationService,
            TokenSettingsService tokenSettingsService,
            ResourceLibraryService resourceLibraryService
    ) {
        this.organizationService = organizationService;
        this.organizationCertificateService = organizationCertificateService;
        this.applicationService = applicationService;
        this.tokenSettingsService = tokenSettingsService;
        this.resourceLibraryService = resourceLibraryService;
    }

    // TODO: Include logic to check user permissions to access the entity in validation methods

    public Integer validateOrganizationId(UUID orgGuid) throws OrgNotFoundException {
        Organization validOrganization = organizationService.get(orgGuid).orElseThrow(
                () -> new OrgNotFoundException("Organization with ID " + orgGuid + " not found")
        );
        return validOrganization.getId();
    }

    public Integer validateOrganizationGroupId(Integer orgId, UUID groupGuid) throws OrgGroupNotFoundException {
        OrganizationGroup validOrganizationGroup =
                organizationService.getOrganizationGroup(orgId, groupGuid).orElseThrow(
                        () -> new OrgGroupNotFoundException("Organization Group with ID " + groupGuid + " not found"));

        return validOrganizationGroup.getId();
    }

    public Integer validateApplicationId(Integer orgId, UUID appGuid) throws AppNotFoundException {
        var e = new AppNotFoundException("Application with ID " + appGuid + " not found");
        Application validApplication = applicationService.get(appGuid).orElseThrow(() -> e);

        if (!Objects.equals(orgId, validApplication.getOrgId())) {
            throw e;
        }

        if (!validApplication.getActive()) {
            throw new AppNotFoundException("Application with ID " + appGuid + " is inactive");
        }

        return validApplication.getId();
    }

    public Integer validateTokenSettings(Integer orgId, Integer appId) throws TokenSettingsNotFoundException {
        var e = new TokenSettingsNotFoundException("Token settings not found for Organization with ID " + orgId + " and Application ID " + appId);
        TokenSettings validTokenSettings = tokenSettingsService.getForApp(orgId, appId).orElseThrow(() -> e);
        return validTokenSettings.getId();
    }

    public Integer validateResourceLibraryId(UUID resourceGuid) throws ResourceLibraryNotFoundException {
        ResourceLibrary validResourceLibrary = resourceLibraryService.get(resourceGuid).orElseThrow(
                () -> new ResourceLibraryNotFoundException("Resource with ID " + resourceGuid + " not found"));
        return validResourceLibrary.getId();
    }

    public Integer validateResourceId(Integer orgId, Integer appId, UUID resourceLibraryGuid) throws AppResourceNotFoundException {
        var e = new AppResourceNotFoundException("Resource with ID " + resourceLibraryGuid + " assigned to this application not found");
        Resource validResource = applicationService.getResource(orgId, appId, resourceLibraryGuid).orElseThrow(() -> e);
        if (!Objects.equals(orgId, validResource.getOrgId())
                || !Objects.equals(appId, validResource.getAppId())) {
            throw e;
        }

        return validResource.getId();
    }

    public UUID validateCertificateId(Integer orgId, UUID certificateGuid) throws CertificateNotFoundException {

        CertificateEntity certificate = organizationCertificateService.get(orgId, certificateGuid).orElseThrow(
                () -> new CertificateNotFoundException("Certificate with ID " + certificateGuid + " not found"));
        return certificate.getId();
    }
}
