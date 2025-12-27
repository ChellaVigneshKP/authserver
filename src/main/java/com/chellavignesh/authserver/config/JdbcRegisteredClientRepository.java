package com.chellavignesh.authserver.config;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRegisteredClientRepository implements RegisteredClientRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcRegisteredClientRepository.class);

    private final ApplicationService applicationService;

    public JdbcRegisteredClientRepository(ApplicationService applicationService) {
        this.applicationService = applicationService;
        log.debug("JdbcRegisteredClientRepository initialized successfully");
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        // Don't want to do this through here
    }

    @Override
    public RegisteredClient findById(String id) {
        return getRegisteredClient(id);
    }

    @Nullable
    private RegisteredClient getRegisteredClient(String clientId) {
        try {
            log.debug("Looking up RegisteredClient for clientId: {}", clientId);

            RegisteredClient registeredClient = applicationService.getRegisteredClientByClientId(clientId);

            if (registeredClient != null) {
                log.debug("RegisteredClient found for clientId: {}", clientId);
            } else {
                log.warn("RegisteredClient NOT FOUND for clientId: {}", clientId);
            }

            return registeredClient;

        } catch (Exception e) {
            log.error("Exception getting RegisteredClient for clientId: {} - Error: {}", clientId, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return getRegisteredClient(clientId);
    }
}

