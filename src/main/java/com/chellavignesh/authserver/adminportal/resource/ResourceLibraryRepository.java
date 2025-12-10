package com.chellavignesh.authserver.adminportal.resource;

import com.chellavignesh.authserver.adminportal.resource.dto.HttpMethodEnum;
import com.chellavignesh.authserver.adminportal.resource.dto.ResourceLibraryDto;
import com.chellavignesh.authserver.adminportal.resource.entity.ResourceLibrary;
import com.chellavignesh.authserver.adminportal.resource.entity.ResourceLibraryRowMapper;
import com.chellavignesh.authserver.adminportal.resource.exception.ResourceLibraryCreationFailedException;
import com.chellavignesh.authserver.adminportal.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ResourceLibraryRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final SecurityUtil securityUtil;

    @Autowired
    public ResourceLibraryRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate, SecurityUtil securityUtil) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.securityUtil = securityUtil;
    }

    public ResourceLibrary create(ResourceLibraryDto dto) throws ResourceLibraryCreationFailedException {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("name", dto.getName());
        parameters.addValue("description", dto.getDescription());
        parameters.addValue("uri", dto.getUri());
        parameters.addValue("allowedMethod", dto.getAllowedMethod());
        parameters.addValue("urn", dto.getUrn());

        Integer resourceId = namedParameterJdbcTemplate.execute(
                "{call dbo.CreateResourceLibrary(:name, :description, :uri, :allowedMethod, :urn)}",
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

        return getById(resourceId).orElseThrow(() -> new ResourceLibraryCreationFailedException("Could not fetch newly created resource by ID."));
    }

    public boolean update(Integer resourceId, ResourceLibraryDto resourceLibraryDto) {

        var parameters = new MapSqlParameterSource();
        parameters.addValue("resourceLibraryId", resourceId);
        parameters.addValue("name", resourceLibraryDto.getName());
        parameters.addValue("description", resourceLibraryDto.getDescription());
        parameters.addValue("uri", resourceLibraryDto.getUri());
        parameters.addValue("allowedMethod", resourceLibraryDto.getAllowedMethod());
        parameters.addValue("urn", resourceLibraryDto.getUrn());
        parameters.addValue("modifiedOn", new Date());
        parameters.addValue("modifiedBy", securityUtil.getTokenUserGuid());

        namedParameterJdbcTemplate.update(
                "{call dbo.UpdateResourceLibrary(:resourceLibraryId, :name, :description, :uri, :allowedMethod, :urn, :modifiedOn, :modifiedBy)}",
                parameters);
        return true;
    }

    public Optional<ResourceLibrary> getById(Integer resourceLibraryId) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("resourceLibraryId", resourceLibraryId);
        return namedParameterJdbcTemplate.query(
                "{call dbo.GetResourceLibraryById(:resourceLibraryId)}",
                parameters,
                new ResourceLibraryRowMapper()
        ).stream().findFirst();
    }

    public boolean exists(String uri, HttpMethodEnum allowedMethod, String urn) {

        var parameters = new MapSqlParameterSource();
        parameters.addValue("uri", uri);
        parameters.addValue("allowedMethod", allowedMethod.toString());
        parameters.addValue("urn", urn);

        List<Integer> resourceLibraryIdList = namedParameterJdbcTemplate.queryForList(
                "{call dbo.GetResourceLibraryByUriMethodAndUrn(:uri, :allowedMethod, :urn)}",
                parameters,
                Integer.class
        );

        return !resourceLibraryIdList.isEmpty();
    }

    public List<ResourceLibrary> getAll() {
        return namedParameterJdbcTemplate.query(
                "{call dbo.GetResourceLibraries()}",
                new MapSqlParameterSource(),
                new ResourceLibraryRowMapper()
        );
    }

    public Optional<ResourceLibrary> get(UUID resourceGuid) {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("resourceGuid", resourceGuid.toString());
        return namedParameterJdbcTemplate.query(
                "{call dbo.GetResourceLibrary(:resourceGuid)}",
                parameters,
                new ResourceLibraryRowMapper()
        ).stream().findFirst();
    }
}
