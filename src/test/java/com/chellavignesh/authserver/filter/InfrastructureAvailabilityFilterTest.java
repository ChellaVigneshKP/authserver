package com.chellavignesh.authserver.filter;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;

class InfrastructureAvailabilityFilterTest {
    @Test void wrappedSessionRedisFailureReturnsUnavailableWithoutLeakingDetails() throws Exception {
        var filter = new InfrastructureAvailabilityFilter();
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/services/oauth2/authorize"), response,
                (request, reply) -> {
                    reply.getWriter().write("partially rendered login");
                    throw new ServletException("session wrapper", new RedisConnectionFailureException("private-host-and-credentials"));
                });
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentAsString()).isEqualTo(InfrastructureAvailabilityFilter.MESSAGE);
    }

    @Test void ordinaryProgrammingFailureIsNotMisreportedAsDependencyOutage() {
        var failure = new IllegalStateException("broken application invariant");
        assertThatThrownBy(() -> new InfrastructureAvailabilityFilter().doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), (request, reply) -> {throw failure;}))
                .isSameAs(failure);
    }

    @Test void timedOutRedisConnectionReturnsUnavailable() throws Exception {
        var response = new MockHttpServletResponse();
        new InfrastructureAvailabilityFilter().doFilter(new MockHttpServletRequest(), response,
                (request, reply) -> {throw new org.springframework.dao.QueryTimeoutException("Redis command timed out");});
        assertThat(response.getStatus()).isEqualTo(503);
    }
    @Test void portalOutageHasSafeRetryableProblem() throws Exception {
        var request=new MockHttpServletRequest("GET","/services/api/v1/portal/context");
        request.setServletPath("/api/v1/portal/context");request.addHeader("x-request-id","outage-request-123");
        var response=new MockHttpServletResponse();
        new InfrastructureAvailabilityFilter().doFilter(request,response,(req,res)-> {
            throw new ServletException("wrapper",new RedisConnectionFailureException("private host and credentials"));
        });
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).contains("application/json");
        var body=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.path("code").asText()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(body.path("retryable").asBoolean()).isTrue();
        assertThat(body.path("requestId").asText()).isEqualTo("outage-request-123");
        assertThat(response.getContentAsString()).doesNotContain("private host","credentials","Exception");
    }

}
