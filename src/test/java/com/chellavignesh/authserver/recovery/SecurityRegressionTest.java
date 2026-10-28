package com.chellavignesh.authserver.recovery;

import com.chellavignesh.authserver.config.PreAuthGrantedAuthority;
import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.config.authorization.CustomOAuth2AuthorizationCodeRequestAuthenticationConverter;
import com.chellavignesh.authserver.config.introspect.TokenIntrospector;
import com.chellavignesh.authserver.security.RequestClientFingerprintFilter;
import com.chellavignesh.authserver.session.PasswordEncoderFactory;
import com.chellavignesh.authserver.token.TokenService;
import com.chellavignesh.authserver.token.entity.Token;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SecurityRegressionTest {
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void loginFingerprintSurvivesTheActualRedisSessionSerialization() {
        var serializer = new com.chellavignesh.authserver.config.SessionConfig().springSessionDefaultRedisSerializer();
        var session = new org.springframework.mock.web.MockHttpSession();
        byte[] fingerprint = {1, 2, 3, 4};
        session.setAttribute(ApplicationConstants.CLIENT_FINGERPRINT, serializer.deserialize(serializer.serialize(fingerprint)));
        var handler = new com.chellavignesh.authserver.config.CustomAuthenticationSuccessHandler(null,null,null,null,null,null,false);
        byte[] restored = org.springframework.test.util.ReflectionTestUtils.invokeMethod(handler,"getClientFingerprint",session);
        assertThat(restored).isEqualTo(fingerprint);
    }

    @Test void authenticatedAuthorizationContinuationCannotChangeBrand() {
        var request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setQueryString("response_type=code&client_id=client&scope=openid&continue&branding=other");
        request.getSession().setAttribute(com.chellavignesh.authserver.config.ApplicationConstants.AUTHENTICATED_BRANDING, "idp");
        request.getSession().setAttribute(com.chellavignesh.authserver.config.ApplicationConstants.BRANDING_INFO, "idp");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of()));
        var converter = new CustomOAuth2AuthorizationCodeRequestAuthenticationConverter(false, true);
        assertThatThrownBy(() -> converter.convert(request))
                .isInstanceOf(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class);
        assertThat(request.getSession().getAttribute(com.chellavignesh.authserver.config.ApplicationConstants.BRANDING_INFO))
                .isEqualTo("idp");
    }

    @Test void successfulLoginPreservesFingerprintBytes() {
        var handler = mock(com.chellavignesh.authserver.config.CustomAuthenticationSuccessHandler.class, CALLS_REAL_METHODS);
        var session = new MockHttpSession();
        byte[] fingerprint = new byte[] {1, 2, 3, 4};
        session.setAttribute(com.chellavignesh.authserver.config.ApplicationConstants.CLIENT_FINGERPRINT, fingerprint);
        byte[] actual = ReflectionTestUtils.invokeMethod(handler, "getClientFingerprint", session);
        assertThat(actual).isEqualTo(fingerprint).isNotSameAs(fingerprint);
    }

    @Test void inactiveTokenIsRejectedRatherThanAuthenticated() {
        TokenService service = mock(TokenService.class);
        Token token = new Token();
        when(service.getByValue(eq("expired"), any())).thenReturn(Optional.of(token));
        when(service.getClaimsForToken(token)).thenReturn(Map.of("active", false));
        assertThatThrownBy(() -> new TokenIntrospector(service).introspect("expired"))
                .isInstanceOf(BadOpaqueTokenException.class);
    }

    @Test void pendingMfaCannotBeUsedToRequestAuthorizationCode() {
        var principal = new UsernamePasswordAuthenticationToken("pending", null, List.of(new PreAuthGrantedAuthority()));
        SecurityContextHolder.getContext().setAuthentication(principal);
        var request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setQueryString("response_type=code&client_id=client&scope=openid&continue");
        request.setParameter("response_type", "code");
        request.setParameter("client_id", "client");
        request.setParameter("scope", "openid");
        request.setParameter("continue", "");
        var converter = new CustomOAuth2AuthorizationCodeRequestAuthenticationConverter(false, true);
        assertThatThrownBy(() -> converter.convert(request))
                .isInstanceOf(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class);
    }

    @Test void publicRequestsWithNoAuthenticationDoNotCrashFingerprintFilter() {
        var request = new MockHttpServletRequest("GET", "/favicon.ico");
        request.setServletPath("/favicon.ico");
        assertThatCode(() -> new RequestClientFingerprintFilter(null).doFilter(request,
                new MockHttpServletResponse(), new MockFilterChain())).doesNotThrowAnyException();
    }

    @Test void passwordFactoryRemovesOnlyEncoderPrefix() {
        try (var context = new StaticApplicationContext()) {
            PasswordEncoder encoder = mock(PasswordEncoder.class);
            when(encoder.encode("test")).thenReturn("{1}0123456789abcdef");
            context.getBeanFactory().registerSingleton("passwordEncoder", encoder);
            var factory = new PasswordEncoderFactory();
            ReflectionTestUtils.setField(factory, "context", context);
            assertThat(factory.encode("test")).isEqualTo("0123456789abcdef");
        }
    }
}
