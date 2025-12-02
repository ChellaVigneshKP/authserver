package com.chellavignesh.authserver.adminportal.util.map.diff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface ValueExtractor<T> {
    @Nullable Object extract(@NotNull T source);
}
