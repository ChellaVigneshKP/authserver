package com.chellavignesh.authserver.token.entity;

import org.junit.jupiter.api.Test;
import java.sql.ResultSet;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TokenMappingRegressionTest {
    @Test
    void brokenProjectionFailsInsteadOfReturningNullToken() throws Exception {
        var row = mock(ResultSet.class);
        when(row.getInt("TokenId")).thenThrow(new SQLException("missing TokenId"));
        assertThrows(SQLException.class, () -> Token.fromResult(row));
    }
}
