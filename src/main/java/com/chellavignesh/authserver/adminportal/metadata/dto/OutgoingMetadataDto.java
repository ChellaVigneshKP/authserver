package com.chellavignesh.authserver.adminportal.metadata.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Getter
public class OutgoingMetadataDto extends HashMap<String, Object> {

    private static final String IDP_PROPERTY_NAME = "idp";
    private static final String UNITE_PROPERTY_NAME = "unite";

    @NotNull
    public static OutgoingMetadataDto createEmpty() {
        return new OutgoingMetadataDto();
    }

    private OutgoingMetadataDto() {
        this(Map.of());
    }

    @JsonCreator
    public OutgoingMetadataDto(@NotNull final Map<String, Object> data) {
        putAll(data);
        ensureMetadataExists(IDP_PROPERTY_NAME, this);
        ensureMetadataExists(UNITE_PROPERTY_NAME, this);
    }

    private static void ensureMetadataExists(@NotNull final String key, @NotNull final OutgoingMetadataDto metadata) {
        final var value = metadata.get(key);

        if (value == null) {
            metadata.put(key, List.of());
        } else if (!(value instanceof List)) {
            log.error("Invalid metadata: Unexpected type of the '{}' property, type='{}'", key, value.getClass().getCanonicalName());
        }
    }
}