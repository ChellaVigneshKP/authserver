package com.chellavignesh.authserver.adminportal.application;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RedirectUriService {
    private final RedirectUriRepository redirectUriRepository;

    public RedirectUriService(RedirectUriRepository redirectUriRepository) {
        this.redirectUriRepository = redirectUriRepository;
    }

    public boolean updateRedirectUri(Integer orgId, Integer appId, String uri) {
        return redirectUriRepository.updateRedirectUri(orgId, appId, uri);
    }

    public boolean createRedirectUris(Integer orgId, Integer appId, List<String> uris) {
        return redirectUriRepository.createRedirectUris(orgId, appId, uris);
    }

}
