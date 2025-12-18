package com.chellavignesh.authserver.adminportal.metadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

@FunctionalInterface
interface JsonReader<T> {

    @Nullable
    T read(@NotNull ObjectMapper objectMapper) throws JsonProcessingException;
}
