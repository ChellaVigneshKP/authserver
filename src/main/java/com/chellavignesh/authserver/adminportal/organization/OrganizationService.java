package com.chellavignesh.authserver.adminportal.organization;

import com.chellavignesh.authserver.adminportal.organization.dto.CreateOrganizationDto;
import com.chellavignesh.authserver.adminportal.organization.dto.UpdateOrganizationContactDto;
import com.chellavignesh.authserver.adminportal.organization.dto.UpdateOrganizationDto;
import com.chellavignesh.authserver.adminportal.organization.entity.Organization;
import com.chellavignesh.authserver.adminportal.organization.entity.OrganizationGroup;
import com.chellavignesh.authserver.adminportal.organization.entity.OrganizationGroupPermission;
import com.chellavignesh.authserver.adminportal.organization.exception.OrgCreationFailedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrganizationService {
    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public Organization create(CreateOrganizationDto createOrganizationDto) throws OrgCreationFailedException {
        if (organizationRepository.existsByName(createOrganizationDto)) {
            throw new OrgCreationFailedException("Organization already exists");
        }
        Organization organization = organizationRepository.create(createOrganizationDto);
        if (organization == null) {
            throw new OrgCreationFailedException("Failed to create organization");
        }
        return organization;
    }

    public List<Organization> getAll() {
        return organizationRepository.getAll();
    }

    public Optional<Organization> get(UUID orgGuid) {
        return organizationRepository.get(orgGuid);
    }

    public Optional<Organization> getById(Integer orgId) {
        return organizationRepository.getById(orgId);
    }

    public boolean exists(UUID orgGuid) {
        return organizationRepository.exists(orgGuid);
    }

    public Optional<OrganizationGroup> getOrganizationGroup(Integer orgId, UUID groupGuid) {
        return organizationRepository.getOrganizationGroup(orgId, groupGuid);
    }

    public boolean updatePrimaryContact(Integer orgId, UpdateOrganizationContactDto contactDto) {
        return organizationRepository.updateOrganizationPrimaryContact(orgId, contactDto);
    }

    public boolean updateSecondaryContact(Integer orgId, UpdateOrganizationContactDto contactDto) {
        return organizationRepository.updateOrganizationSecondaryContact(orgId, contactDto);
    }

    public boolean update(Integer orgId, UpdateOrganizationDto dto) {
        return organizationRepository.updateOrganization(orgId, dto);
    }

    public List<OrganizationGroup> getOrganizationGroups(Integer orgId) {
        return organizationRepository.getOrganizationGroups(orgId);
    }

    public List<OrganizationGroupPermission> getOrganizationGroupPermissions(Integer orgId, Integer orgGroupId) {
        return organizationRepository.getOrganizationGroupPermissions(orgId, orgGroupId);
    }
}
