package com.chellavignesh.authserver.adminportal.externalsource;


import com.chellavignesh.authserver.adminportal.externalsource.entity.ExternalSource;
import com.chellavignesh.authserver.adminportal.externalsource.entity.ExternalSourceRowMapper;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class ExternalSourceRepository {

    private static final ExternalSourceRowMapper ROW_MAPPER = new ExternalSourceRowMapper();

    @NotNull
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public ExternalSourceRepository(@NotNull NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @NotNull
    public List<ExternalSource> findAllByBrandingSourceCode(@NotNull final List<String> brandingSourceCodes) {

        final var processedBrandingIds = brandingSourceCodes.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining(","));

        final var parameters = new MapSqlParameterSource();
        parameters.addValue("Branding", processedBrandingIds);

        return namedParameterJdbcTemplate.query(
                "{call dbo.GetExternalSource(:Branding)}",
                parameters,
                ROW_MAPPER
        );
    }

    @NotNull
    public Optional<ExternalSource> findBySourceId(@NotNull final UUID sourceId) {

        final var parameters = new MapSqlParameterSource();
        parameters.addValue("SourceId", sourceId);

        return namedParameterJdbcTemplate.query(
                "{call dbo.GetExternalSourceById(:SourceId)}",
                parameters,
                ROW_MAPPER
        ).stream().findFirst();
    }
}
