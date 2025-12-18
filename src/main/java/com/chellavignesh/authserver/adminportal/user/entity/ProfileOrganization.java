package com.chellavignesh.authserver.adminportal.user.entity;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.RowMapper;

import java.util.Optional;
import java.util.UUID;

@Data
public class ProfileOrganization {

    public static final RowMapper<ProfileOrganization> MAPPER = (rs, rowNum) -> {
        final var toReturn = new ProfileOrganization();

        toReturn.setProfileOrganizationId(rs.getInt("ProfileOrganizationId"));
        toReturn.setProfileId(rs.getInt("ProfileId"));
        toReturn.setOrganizationId(rs.getInt("OrganizationId"));

        Optional.ofNullable(rs.getString("RowGuid"))
                .filter(StringUtils::isNotBlank)
                .map(UUID::fromString)
                .ifPresent(toReturn::setRowGuid);

        Optional.ofNullable(rs.getString("OrganizationRowGuid"))
                .filter(StringUtils::isNotBlank)
                .map(UUID::fromString)
                .ifPresent(toReturn::setOrganizationRowGuid);

        return toReturn;
    };

    private Integer profileOrganizationId;
    private Integer profileId;
    private Integer organizationId;
    private UUID rowGuid;
    private UUID organizationRowGuid;
}
