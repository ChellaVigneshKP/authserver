package com.chellavignesh.authserver.adminportal.resource;

import com.chellavignesh.authserver.adminportal.application.exception.ResourceLibraryDataAccessException;
import com.chellavignesh.authserver.adminportal.resource.dto.HttpMethodEnum;
import com.chellavignesh.authserver.adminportal.resource.dto.ResourceLibraryDto;
import com.chellavignesh.authserver.adminportal.resource.entity.ResourceLibrary;
import com.chellavignesh.authserver.adminportal.resource.exception.ResourceLibraryCreationFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ResourceLibraryService {
    private final ResourceLibraryRepository resourceLibraryRepository;

    @Autowired
    public ResourceLibraryService(ResourceLibraryRepository resourceLibraryRepository) {
        this.resourceLibraryRepository = resourceLibraryRepository;
    }

    public ResourceLibrary create(ResourceLibraryDto createResourceLibraryDto) throws ResourceLibraryCreationFailedException {

        if (resourceLibraryRepository.exists(
                createResourceLibraryDto.getUri(),
                createResourceLibraryDto.getAllowedMethodEnum(),
                createResourceLibraryDto.getUrn()
        )) {
            throw new DataIntegrityViolationException("Resource with this URI/Method/URN already exists.");
        }

        ResourceLibrary resource = resourceLibraryRepository.create(createResourceLibraryDto);

        if (resource == null) {
            throw new ResourceLibraryCreationFailedException("Failed to create new organization.");
        }

        return resource;
    }

    public Optional<ResourceLibrary> getById(Integer orgId) {
        return resourceLibraryRepository.getById(orgId);
    }

    public boolean exists(String uri, HttpMethodEnum method, String urn) {
        return resourceLibraryRepository.exists(uri, method, urn);
    }

    public List<ResourceLibrary> getAll() {
        return resourceLibraryRepository.getAll();
    }

    public Optional<ResourceLibrary> get(UUID resourceGuid) {
        return resourceLibraryRepository.get(resourceGuid);
    }

    public boolean update(Integer resourceLibraryId, ResourceLibraryDto resourceLibraryDto) throws ResourceLibraryDataAccessException {

        ResourceLibrary resourceLibrary = resourceLibraryRepository.getById(resourceLibraryId).get();

        if ((!resourceLibrary.getUri().equalsIgnoreCase(resourceLibraryDto.getUri()))
                || !resourceLibrary.getAllowedMethod().equalsIgnoreCase(resourceLibraryDto.getAllowedMethod())
                || (!resourceLibrary.getUrn().equalsIgnoreCase(resourceLibraryDto.getUrn())
                && resourceLibraryDto.getUrn() != null
                && resourceLibraryRepository.exists(
                resourceLibraryDto.getUri(),
                resourceLibraryDto.getAllowedMethodEnum(),
                resourceLibraryDto.getUrn()
        ))) {

            throw new DataIntegrityViolationException("Resource with this URI/Allowed Method/URN already exists.");
        }

        try {
            return this.resourceLibraryRepository.update(resourceLibraryId, resourceLibraryDto);
        } catch (Exception exception) {
            throw new ResourceLibraryDataAccessException("Error while updating Resource with ID: " + resourceLibraryId, exception);
        }
    }
}
