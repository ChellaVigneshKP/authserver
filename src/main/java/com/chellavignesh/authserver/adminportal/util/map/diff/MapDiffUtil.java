package com.chellavignesh.authserver.adminportal.util.map.diff;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@UtilityClass
public class MapDiffUtil {

    public static <T, S> Map<String, Object> createDiffFor(
            @NotNull final T left,
            @NotNull final S right,
            @NotNull final List<PropertyExtractor<T, S>> propertyExtractors) {

        final var diff = new HashMap<String, Object>();
        propertyExtractors.forEach(p -> {
            final var leftValue = p.leftValueExtractor().extract(left);
            final var rightValue = p.rightValueExtractor().extract(right);
            addPropertyWhenDifferent(diff, p.name(), leftValue, rightValue);
        });

        return Collections.unmodifiableMap(diff);
    }

    private static void addPropertyWhenDifferent(
            @NotNull final Map<String, Object> properties,
            @NotNull final String name,
            @Nullable final Object oldValue,
            @Nullable final Object newValue) {

        if (!Objects.equals(oldValue, newValue)) {
            properties.put(name, newValue);
        }
    }
}
