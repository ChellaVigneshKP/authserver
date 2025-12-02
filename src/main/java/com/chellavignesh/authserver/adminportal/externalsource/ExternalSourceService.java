package com.chellavignesh.authserver.adminportal.externalsource;

import com.chellavignesh.authserver.adminportal.externalsource.entity.ExternalSource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class ExternalSourceService {
    private final ExternalSourceRepository externalSourceRepository;

    @Autowired
    public ExternalSourceService(@NotNull ExternalSourceRepository externalSourceRepository) {
        this.externalSourceRepository = externalSourceRepository;
    }

    @NotNull
    @Cacheable(cacheNames = "external-source-by-code", key = "#sourceCode")
    public Optional<ExternalSource> findBySourceCode(@NotNull String sourceCode) {
        log.debug("Fetching external source by source code: {}", sourceCode);
        return externalSourceRepository.findAllByBrandingSourceCode(List.of(sourceCode)).stream().findFirst();
    }

    @NotNull
    public List<ExternalSource> findAllBySourceCodes(@NotNull List<String> sourceCodes) {
        return externalSourceRepository.findAllByBrandingSourceCode(sourceCodes);
    }

    @NotNull
    public Optional<ExternalSource> findBySourceId(@NotNull UUID sourceId) {
        return externalSourceRepository.findBySourceId(sourceId);
    }
}
