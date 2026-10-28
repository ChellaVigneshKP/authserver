package com.chellavignesh.authserver.controller;

import com.chellavignesh.authserver.cms.BrandUrlMap;
import com.chellavignesh.authserver.cms.BrandUrlMappingService;
import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OidcOrgConfigControllerTest {
    @Test void discoveryUsesSingleOauthSegmentWithServletContext() throws Exception {
        var service = new BrandUrlMappingService(new BrandUrlMap(null), URI.create("http://localhost:9080/services/"));
        standaloneSetup(new OidcOrgConfigController(service)).build()
                .perform(get("/services/oauth2/.well-known/organizations/11111111-1111-1111-1111-111111111111/openid-configuration").contextPath("/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorization_endpoint").value("http://localhost:9080/services/oauth2/authorize"))
                .andExpect(jsonPath("$.jwks_uri").value("http://localhost:9080/services/oauth2/jwks"));
    }
}
