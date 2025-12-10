package com.chellavignesh.authserver.adminportal.resource;

import com.chellavignesh.authserver.adminportal.resource.dto.ResourceLibraryDto;
import com.chellavignesh.authserver.adminportal.resource.entity.ResourceLibrary;
import com.chellavignesh.authserver.adminportal.resource.entity.ResourceLibraryRowMapper;
import com.chellavignesh.authserver.adminportal.resource.exception.ResourceLibraryCreationFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.Optional;

@Repository
public class ResourceRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public ResourceRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public ResourceLibrary create(ResourceLibraryDto dto) throws ResourceLibraryCreationFailedException {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("name", dto.getName());
        parameters.addValue("description", dto.getDescription());
        parameters.addValue("uri", dto.getUri());
        parameters.addValue("urn", dto.getUrn());
        parameters.addValue("allowedMethod", dto.getAllowedMethod());

        Integer resourceId = namedParameterJdbcTemplate.execute(
                "{call Resource.CreateResource(:name, :description, :uri, :urn, :allowedMethod)}",
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

        return getById(resourceId).orElseThrow(
                () -> new ResourceLibraryCreationFailedException("Could not fetch newly created resource by ID.")
        );
    }

    public Optional<ResourceLibrary> getById(Integer resourceId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("resourceId", resourceId);
        return namedParameterJdbcTemplate.query(
                "{call Partner.GetResourceById(:orgId)}",
                parameters,
                new ResourceLibraryRowMapper()
        ).stream().findFirst();
    }
}
