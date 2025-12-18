package com.chellavignesh.authserver.adminportal.metadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class MetadataService {

    private static final String METADATA_ATTRIBUTE = "mt";

    private final ObjectMapper objectMapper;

    @Autowired
    public MetadataService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> Optional<T> tryReadFromSessionAsType(HttpServletRequest request, Class<T> type) {

        return Optional.ofNullable((String) request.getSession().getAttribute(METADATA_ATTRIBUTE)).flatMap(p -> tryReadAsType(p, type));
    }

    public <T> Optional<T> tryReadAsType(String json, Class<T> type) {
        return read(mapper -> mapper.readValue(json, type));
    }

    public boolean isMetadataParameterInvalid(Map<String, String> parameters, String errorMessagePrefix) {

        boolean invalid = Optional.ofNullable(parameters.get(METADATA_ATTRIBUTE)).filter(StringUtils::isNotBlank).flatMap(this::tryReadAsTree).filter(JsonNode::isObject).isEmpty();

        if (invalid) {
            log.error("{}: Metadata parameter '{}' is missing", errorMessagePrefix, METADATA_ATTRIBUTE);
        }

        return invalid;
    }

    private Optional<JsonNode> tryReadAsTree(String json) {
        return read(mapper -> mapper.readTree(json));
    }

    private <T> Optional<T> read(JsonReader<T> reader) {
        try {
            return Optional.ofNullable(reader.read(objectMapper));
        } catch (JsonProcessingException e) {
            log.error("Unable to read metadata from request", e);
            return Optional.empty();
        }
    }
}
