package com.chellavignesh.authserver.cms;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.cms.exception.CmsBadRequestException;
import com.chellavignesh.authserver.config.ApplicationConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

class CmsServiceTest {
    private CmsService service() {
        var service = new CmsService(null, new DefaultResourceLoader());
        ReflectionTestUtils.setField(service, "fileLocation", "classpath:cms/");
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        return service;
    }
    @Test void loadsPackagedCmsWithoutSourceTreeOrClientLookup() throws Exception {
        var request = new MockHttpServletRequest();
        request.getSession().setAttribute(ApplicationConstants.BRANDING_INFO, "idp");
        assertThat(service().getCmsInfoForRequest(request)).containsEntry("planName", "Identity services");
    }
    @Test void rejectsTraversalAndMissingBrand() {
        var request = new MockHttpServletRequest();
        assertThatThrownBy(() -> service().getCmsInfoForRequest(request)).isInstanceOf(CmsBadRequestException.class);
        request.getSession().setAttribute(ApplicationConstants.BRANDING_INFO, "../application");
        assertThatThrownBy(() -> service().getCmsInfoForRequest(request)).isInstanceOf(CmsBadRequestException.class);
    }
}
