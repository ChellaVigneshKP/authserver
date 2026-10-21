package com.chellavignesh.authserver.config.introspect;

import com.chellavignesh.authserver.token.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TokenIntrospectorTest {
    @Test void dependencyOutageIsNotAnInvalidBearer() {
        var tokens=mock(TokenService.class);
        var outage=new DataAccessResourceFailureException("private connection details");
        when(tokens.getByValue(anyString(),any())).thenThrow(outage);
        assertThatThrownBy(()->new TokenIntrospector(tokens).introspect("opaque")).isSameAs(outage);
    }
    @Test void missingBearerRemainsInvalid() {
        var tokens=mock(TokenService.class);when(tokens.getByValue(anyString(),any())).thenReturn(Optional.empty());
        assertThatThrownBy(()->new TokenIntrospector(tokens).introspect("opaque")).isInstanceOf(BadOpaqueTokenException.class);
    }
}
