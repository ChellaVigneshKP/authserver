package com.chellavignesh.authserver.security;

import com.chellavignesh.authserver.adminportal.externalsource.ExternalSourceService;
import com.chellavignesh.authserver.adminportal.externalsource.entity.ExternalSource;
import com.chellavignesh.authserver.config.ApplicationConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;

class BrandingRequestBodyFilterTest {
    @Test void loginWrapsUsernameUnderContextAndRoot() throws Exception {
        for (var context : new String[]{"", "/services"}) {
            var sourceService = new ExternalSourceService(null) {
                @Override public Optional<ExternalSource> findBySourceCode(String code) {
                    assertThat(code).isEqualTo("idp");
                    return Optional.of(new ExternalSource());
                }
            };
            var request = new MockHttpServletRequest("POST", context + "/login");
            request.setContextPath(context);
            request.addParameter("username", "alice");
            request.getSession().setAttribute(ApplicationConstants.BRANDING_INFO, "idp");
            var response = new MockHttpServletResponse();
            new BrandingRequestBodyFilter(context, sourceService).doFilter(request, response,
                    (wrapped, ignored) -> assertThat(wrapped.getParameter("username")).isEqualTo("alice" + System.lineSeparator() + "idp"));
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
    @Test void unrelatedSuffixIsNotLoginAndMissingBrandFails() throws Exception {
        var service = new ExternalSourceService(null) {
            @Override public Optional<ExternalSource> findBySourceCode(String code) {
                throw new AssertionError("Unrelated or unbranded requests must not query source data");
            }
        };
        var filter = new BrandingRequestBodyFilter("", service);
        var unrelated = new MockHttpServletRequest("POST", "/api/not/login");
        filter.doFilter(unrelated, new MockHttpServletResponse(), (request, response) -> {});
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/login"), response, (request, ignored) -> fail("Must not reach authentication without branding"));
        assertThat(response.getStatus()).isEqualTo(400);
    }
}
