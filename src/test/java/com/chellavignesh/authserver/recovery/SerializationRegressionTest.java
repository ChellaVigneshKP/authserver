package com.chellavignesh.authserver.recovery;

import com.chellavignesh.authserver.config.RegisteredClientRedisSerializer;
import com.chellavignesh.authserver.config.SessionConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SerializationRegressionTest {
    @Test void profileValidationErrorsSurviveTheRedirectSession() {
        var serializer = new SessionConfig().springSessionDefaultRedisSerializer();
        var profile = new com.chellavignesh.authserver.adminportal.user.dto.ChangeProfileDto();
        profile.setEmail("invalid-address");
        Object restored = serializer.deserialize(serializer.serialize(profile));
        assertThat(restored).isInstanceOf(com.chellavignesh.authserver.adminportal.user.dto.ChangeProfileDto.class);
        assertThat(((com.chellavignesh.authserver.adminportal.user.dto.ChangeProfileDto) restored).getEmail())
                .isEqualTo("invalid-address");
    }

    @Test void mfaPrincipalAndAuthoritySurviveTheSession() {
        var user = new com.chellavignesh.authserver.adminportal.user.entity.UserAuthDetails();
        user.setUserName("alice"); user.setPassword("hash"); user.setVersion(1);
        user.setUserStatus(1); user.setTwoFactorEnabled(true);
        var context = new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                new com.chellavignesh.authserver.session.dto.CustomUserDetails(user), null,
                List.of(new com.chellavignesh.authserver.config.PreAuthGrantedAuthority())));
        var serializer = new SessionConfig().springSessionDefaultRedisSerializer();
        var restored = (SecurityContextImpl) serializer.deserialize(serializer.serialize(context));
        assertThat(restored.getAuthentication().getName()).isEqualTo("alice");
        assertThat(restored.getAuthentication().getAuthorities())
                .extracting(org.springframework.security.core.GrantedAuthority::getAuthority).containsExactly("PRE_AUTH");
    }

    @Test void redisClientRetainsCustomTokenSettings() {
        var client = RegisteredClient.withId("c").clientId("c")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://example.test/cb")
                .clientSettings(ClientSettings.builder().jwkSetUrl("https://example.test/jwks").build())
                .tokenSettings(TokenSettings.builder().setting("max_request_transit_time", 30).build()).build();
        var serializer = new RegisteredClientRedisSerializer();
        RegisteredClient restored = serializer.deserialize(serializer.serialize(client));
        assertThat(restored.getTokenSettings().getSettings()).isEqualTo(client.getTokenSettings().getSettings());
    }

    @Test void redisSessionSupportsLongMetadataValues() {
        var serializer = new SessionConfig().springSessionDefaultRedisSerializer();
        var metadata = new java.util.HashMap<String, Object>();
        metadata.put("lastAccessedTime", 1789123123123L);
        assertThat(serializer.deserialize(serializer.serialize(metadata))).isEqualTo(metadata);
    }

    @Test void redisSessionRestoresSecurityContextRatherThanUntypedMap() {
        var serializer = new SessionConfig().springSessionDefaultRedisSerializer();
        var context = new SecurityContextImpl(new UsernamePasswordAuthenticationToken("test", null, List.of()));
        assertThat(serializer.deserialize(serializer.serialize(context)))
                .isInstanceOf(SecurityContextImpl.class)
                .extracting(value -> ((SecurityContextImpl)value).getAuthentication().getName()).isEqualTo("test");
    }
}
