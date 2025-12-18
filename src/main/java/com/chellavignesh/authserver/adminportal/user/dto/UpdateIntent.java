package com.chellavignesh.authserver.adminportal.user.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum UpdateIntent {

    UNITE("unite"), IDP("idp"), CONVERSION("conversion");

    @JsonValue
    @NotNull
    private final String id;

    // OPTIMIZATION: Pre-computed lookup map for O(1) access
    private static final Map<String, UpdateIntent> ID_LOOKUP = Stream.of(values()).collect(Collectors.toUnmodifiableMap(UpdateIntent::getId, intent -> intent));

    @JsonCreator
    @Nullable
    public static UpdateIntent forValue(@Nullable String value) {
        return Optional.ofNullable(value).filter(StringUtils::hasText).flatMap(UpdateIntent::findById).orElse(null);
    }

    @NotNull
    private static Optional<UpdateIntent> findById(@NotNull String id) {
        // BEFORE:
        // Stream.of(UpdateIntent.values())
        //     .filter(p -> p.getId().equals(id))
        //     .findFirst();
        // Time: O(n) — iterates through all enum values

        // AFTER:
        // ID_LOOKUP.get(id)
        // Time: O(1) — direct hash map lookup
        return Optional.ofNullable(ID_LOOKUP.get(id));
    }
}

