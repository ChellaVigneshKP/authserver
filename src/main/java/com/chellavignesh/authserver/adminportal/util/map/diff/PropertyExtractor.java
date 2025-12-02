package com.chellavignesh.authserver.adminportal.util.map.diff;

import org.jetbrains.annotations.NotNull;

public record PropertyExtractor<T, S>(@NotNull String name,
                                      @NotNull ValueExtractor<T> leftValueExtractor,
                                      @NotNull ValueExtractor<S> rightValueExtractor) {
}
