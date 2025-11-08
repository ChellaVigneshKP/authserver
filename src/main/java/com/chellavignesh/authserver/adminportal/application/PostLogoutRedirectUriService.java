package com.chellavignesh.authserver.adminportal.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostLogoutRedirectUriService {
    private final PostLogoutRedirectUriRepository postLogoutRedirectUriRepository;

    @Autowired
    public PostLogoutRedirectUriService(PostLogoutRedirectUriRepository postLogoutRedirectUriRepository) {
        this.postLogoutRedirectUriRepository = postLogoutRedirectUriRepository;
    }

    public boolean updatePostLogoutRedirectUri(Integer orgId, Integer appId, String uri) {
        return postLogoutRedirectUriRepository.updatePostLogoutRedirectUri(orgId, appId, uri);
    }

    public boolean createPostLogoutRedirectUris(Integer orgId, Integer appId, List<String> uris) {
        return postLogoutRedirectUriRepository.createPostLogoutRedirectUris(orgId, appId, uris);
    }
}
