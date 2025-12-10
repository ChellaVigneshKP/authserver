package com.chellavignesh.authserver.adminportal.util;

import org.jetbrains.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public class MapSqlParameterBuilder {
    private final MapSqlParameterSource paramSource = new MapSqlParameterSource();

    public MapSqlParameterBuilder addValue(final String key, @Nullable Object value) {
        paramSource.addValue(key, value);
        return this;
    }

    public MapSqlParameterSource build() {
        return paramSource;
    }

    public static MapSqlParameterSource of(String key, Object value) {
        return new MapSqlParameterBuilder().addValue(key, value).build();
    }

    public static MapSqlParameterSource of(String key1, Object value1, String key2, Object value2) {
        return new MapSqlParameterBuilder().addValue(key1, value1).addValue(key2, value2).build();
    }

    public static MapSqlParameterSource of(String key1, Object value1, String key2, Object value2, String key3, Object value3) {
        return new MapSqlParameterBuilder().addValue(key1, value1).addValue(key2, value2).addValue(key3, value3).build();
    }
}
