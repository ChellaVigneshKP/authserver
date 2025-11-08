package com.chellavignesh.authserver.adminportal.organization;

import com.chellavignesh.authserver.adminportal.organization.dto.CreateOrganizationDto;
import com.chellavignesh.authserver.adminportal.organization.dto.UpdateOrganizationContactDto;
import com.chellavignesh.authserver.adminportal.organization.dto.UpdateOrganizationDto;
import com.chellavignesh.authserver.adminportal.organization.entity.*;
import com.chellavignesh.authserver.adminportal.organization.exception.OrgCreationFailedException;
import com.chellavignesh.authserver.adminportal.util.SecurityUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OrganizationRepository {
    private static final Logger log = LoggerFactory.getLogger(OrganizationRepository.class);
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final SecurityUtil securityUtil;

    @Autowired
    public OrganizationRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate, SecurityUtil securityUtil) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.securityUtil = securityUtil;
    }

    public Organization create(CreateOrganizationDto dto) throws OrgCreationFailedException {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("name", dto.getName());
        parameters.addValue("description", dto.getDescription());

        Integer orgId = namedParameterJdbcTemplate.execute(
                "{call Partner.CreateOrganization(:name, :description)}",
                parameters,
                cs -> {
                    try (ResultSet rs = cs.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt("ID");
                        }
                        return null;
                    }
                }
        );

        return getById(orgId).orElseThrow(
                () -> new OrgCreationFailedException("Failed to create organization")
        );
    }

    public boolean existsByName(CreateOrganizationDto dto) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgName", dto.getName());
        return namedParameterJdbcTemplate.query(
                "{call Partner.GetOrganizationByName(:orgName)}",
                parameters,
                new OrganizationRowMapper()
        ).stream().findFirst().isPresent();
    }

    public Optional<Organization> get(UUID orgGuid) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgGuid", orgGuid.toString());
        return namedParameterJdbcTemplate.query(
                "{call Partner.GetOrganization(:orgGuid)}",
                parameters,
                new OrganizationRowMapper()
        ).stream().findFirst();
    }


    @Cacheable("organization-get-by-id")
    public Optional<Organization> getById(Integer orgId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        return namedParameterJdbcTemplate.query(
                "{call Partner.GetOrganizationById(:orgId)}",
                parameters,
                new OrganizationRowMapper()
        ).stream().findFirst();
    }

    public List<Organization> getAll() {
        return namedParameterJdbcTemplate.query(
                "{call Partner.GetOrganizations()}",
                new MapSqlParameterSource(),
                new OrganizationRowMapper()
        );
    }

    public boolean exists(UUID orgGuid) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgGuid", orgGuid.toString());
        return namedParameterJdbcTemplate.query(
                "{call Partner.OrganizationExists(:orgGuid)}",
                parameters,
                (RowMapper<Integer>) (rs, rowNum) -> rs.getInt("OrganizationId")
        ).stream().findFirst().isPresent();
    }

    public Optional<OrganizationGroup> getOrganizationGroup(Integer orgId, UUID groupGuid) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("groupGuid", groupGuid.toString());
        var a = namedParameterJdbcTemplate.query(
                "{call Partner.GetOrganizationGroup(:orgId, :groupGuid)}",
                parameters,
                new OrganizationGroupRowMapper()
        );
        return a.stream().findFirst();
    }

    @CacheEvict(value = "organization-get-by-id", key = "#orgId")
    public boolean updateOrganizationPrimaryContact(Integer orgId, UpdateOrganizationContactDto primaryContact) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("primaryContactName", primaryContact.getName());
        parameters.addValue("primaryContactEmail", primaryContact.getEmail());
        parameters.addValue("primaryContactPhoneNumber", primaryContact.getPhoneNumber());
        parameters.addValue("modifiedBy", securityUtil.getTokenUserGuid());
        parameters.addValue("modifiedOn", new Date());
        namedParameterJdbcTemplate.update(
                "{call Partner.UpdateOrganizationPrimaryContact(:orgId, :primaryContactName, :primaryContactEmail, :primaryContactPhoneNumber, :modifiedOn, :modifiedBy)}",
                parameters
        );
        log.info("Updated primary contact for organization {}", orgId);
        return true;
    }


    @CacheEvict(value = "organization-get-by-id", key = "#orgId")
    public boolean updateOrganizationSecondaryContact(Integer orgId, @Valid UpdateOrganizationContactDto secondaryContact) {
        var parameters = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("secondaryContactName", secondaryContact.getName())
                .addValue("secondaryContactEmail", secondaryContact.getEmail())
                .addValue("secondaryContactPhoneNumber", secondaryContact.getPhoneNumber())
                .addValue("modifiedOn", new Date())
                .addValue("modifiedBy", securityUtil.getTokenUserGuid());
        namedParameterJdbcTemplate.update(
                "{call Partner.UpdateOrganizationSecondaryContact(:orgId, :secondaryContactName, :secondaryContactEmail, :secondaryContactPhoneNumber, :modifiedOn, :modifiedBy)}",
                parameters
        );
        log.info("Updated secondary contact for organization {}", orgId);
        return true;
    }

    @Caching(evict = {
            @CacheEvict(value = "organization-get-by-id", key = "#orgId"),
            @CacheEvict(value = "registered-client-by-client-id", allEntries = true)
    })
    public boolean updateOrganization(Integer orgId, @Valid UpdateOrganizationDto updateOrganizationDto) {
        var parameters = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("name", updateOrganizationDto.getName())
                .addValue("desc", updateOrganizationDto.getDescription())
                .addValue("status", updateOrganizationDto.getStatus().equalsIgnoreCase("Active") ? OrganizationStatus.ACTIVE.getValue() : OrganizationStatus.INACTIVE.getValue())
                .addValue("modifiedOn", new Date())
                .addValue("modifiedBy", securityUtil.getTokenUserGuid());
        namedParameterJdbcTemplate.update(
                "{call Partner.UpdateOrganization(:orgId, :name, :desc, :status, :modifiedOn, :modifiedBy)}",
                parameters
        );
        log.warn("Cleared all registered client cache due to organization {} update", orgId);
        return true;
    }

    public List<OrganizationGroup> getOrganizationGroups(Integer orgId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        return namedParameterJdbcTemplate.query(
                "{call Partner.GetOrganizationGroups(:orgId)}",
                parameters,
                new OrganizationGroupRowMapper()
        );
    }

    public List<OrganizationGroupPermission> getOrganizationGroupPermissions(Integer orgId, Integer orgGroupId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("orgId", orgId);
        parameters.addValue("orgGroupId", orgGroupId);
        return namedParameterJdbcTemplate.query(
                "{call Partner.GetOrganizationGroupPermissions(:orgId, :orgGroupId)}",
                parameters,
                new OrganizationGroupPermissionRowMapper()
        );
    }

}
