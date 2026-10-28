package com.chellavignesh.authserver.controller;

import com.chellavignesh.authserver.config.PreAuthGrantedAuthority;
import com.chellavignesh.authserver.config.SessionConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MfaSessionPersistenceTest {
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void verifiedMfaExplicitlySavesTheCompletedContextForTheNextRequest() {
        var controller = mock(MFAController.class, CALLS_REAL_METHODS);
        var pending = UsernamePasswordAuthenticationToken.authenticated("alice", null,
                List.of(new PreAuthGrantedAuthority(), new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(pending);
        var request = new MockHttpServletRequest();
        controller.updateAuthentication(pending, true, request, new MockHttpServletResponse());
        Object saved = request.getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(saved).isNotNull();
        var serializer = new SessionConfig().springSessionDefaultRedisSerializer();
        SecurityContext nextRequest = (SecurityContext) serializer.deserialize(serializer.serialize(saved));
        assertThat(nextRequest.getAuthentication().isAuthenticated()).isTrue();
        assertThat(nextRequest.getAuthentication().getAuthorities())
                .extracting(org.springframework.security.core.GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }
}
