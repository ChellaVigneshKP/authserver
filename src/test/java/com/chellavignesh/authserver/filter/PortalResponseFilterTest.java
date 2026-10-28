package com.chellavignesh.authserver.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class PortalResponseFilterTest {
    @Test void escapingPortalFailureHasSafeProblemWithoutPartialResponse() throws Exception {
        var request=new MockHttpServletRequest("GET","/services/api/v1/portal/context");
        request.setServletPath("/api/v1/portal/context");request.addHeader("x-request-id","failure-request-123");
        var response=new MockHttpServletResponse();
        assertThatCode(()->new PortalResponseFilter(null).doFilter(request,response,(req,res)-> {
            res.getWriter().write("private partial response");
            throw new ServletException("private upstream details",new IllegalStateException("private cause"));
        })).doesNotThrowAnyException();
        assertThat(response.getStatus()).isEqualTo(500);
        var body=new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.path("code").asText()).isEqualTo("REQUEST_FAILED");
        assertThat(body.path("requestId").asText()).isEqualTo("failure-request-123");
        assertThat(body.path("retryable").asBoolean()).isFalse();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentAsString()).doesNotContain("private","Exception");
    }
    @Test void normalizationFailureStillHasSafeProblem() throws Exception {
        var signatures=org.mockito.Mockito.mock(com.chellavignesh.authserver.token.SignatureService.class);
        org.mockito.Mockito.when(signatures.signBody(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("private signing failure"));
        var request=new MockHttpServletRequest("GET","/services/api/v1/portal/context");
        request.setServletPath("/api/v1/portal/context");request.addHeader("Authorization","Bearer fixture");
        var response=new MockHttpServletResponse();
        assertThatCode(()->new PortalResponseFilter(signatures).doFilter(request,response,(req,res)-> {
            var reply=(jakarta.servlet.http.HttpServletResponse)res;
            reply.setStatus(403);reply.setHeader("x-signature","stale signature");
            reply.getWriter().write("private partial body");
        })).doesNotThrowAnyException();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(new ObjectMapper().readTree(response.getContentAsString()).path("code").asText()).isEqualTo("REQUEST_FAILED");
        assertThat(response.getHeader("x-signature")).isNullOrEmpty();
        assertThat(response.getContentAsString()).doesNotContain("private","Exception");
    }
    @Test void typedDependencyOutageStillReachesAvailabilityHandler() {
        var request=new MockHttpServletRequest("GET","/services/api/v1/portal/context");
        request.setServletPath("/api/v1/portal/context");
        var outage=new DataAccessResourceFailureException("private database details");
        assertThatThrownBy(()->new PortalResponseFilter(null).doFilter(request,new MockHttpServletResponse(),
                (req,res)->{throw outage;})).isSameAs(outage);
    }
    @Test void escapingLegacyFailureKeepsExistingBoundary() {
        var request=new MockHttpServletRequest("GET","/services/api/v1/users");request.setServletPath("/api/v1/users");
        var failure=new IllegalStateException("legacy failure");
        assertThatThrownBy(()->new PortalResponseFilter(null).doFilter(request,new MockHttpServletResponse(),
                (req,res)->{throw failure;})).isSameAs(failure);
    }
    @Test void credentialMatcherIsBoundedToTheV2Collection() {
        String route="/api/v2/organizations/tenant/applications/client/credentials";
        for(String path:new String[]{route,route+"/"}) {
            var request=new MockHttpServletRequest();request.setServletPath(path);
            assertThat(com.chellavignesh.authserver.adminportal.util.PortalResponses.isPortalRequest(request)).isTrue();
        }
        for(String path:new String[]{route+"/id",route+"/extra/path",route+"-extra",route.replace("/v2/","/v1/"),"/api/v2/organizations/tenant/applications/client/secrets"}) {
            var request=new MockHttpServletRequest();request.setServletPath(path);
            assertThat(com.chellavignesh.authserver.adminportal.util.PortalResponses.isPortalRequest(request)).isFalse();
        }
    }

}
