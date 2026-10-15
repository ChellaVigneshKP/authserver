package com.chellavignesh.authserver.cms;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.net.URI;
import static org.assertj.core.api.Assertions.*;

class BrandUrlMappingServiceTest {
    @Test void absentMappingsAndNullBrandUsePublicBaseWithoutCreatingSession() {
        var service = new BrandUrlMappingService(new BrandUrlMap(null), URI.create("http://localhost:9080/services"));
        assertThat(service.getUrlByBrand(null)).isEqualTo(service.getDefaultUrl());
        assertThat(service.getUrlByBrand("unknown")).isEqualTo(service.getDefaultUrl());
        assertThat(service.isDefault(null)).isTrue();
        var request = new MockHttpServletRequest();
        assertThat(service.getBrandFromRequest(request)).isNull();
        assertThat(request.getSession(false)).isNull();
    }
}
