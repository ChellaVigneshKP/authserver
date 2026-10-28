package com.chellavignesh.authserver.adminportal.application;

import com.chellavignesh.authserver.adminportal.application.dto.ApplicationDetailResponseDto;
import com.chellavignesh.authserver.adminportal.application.entity.ApplicationDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.sql.ResultSet;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientSettingsReadbackTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void detailExposesPersistedRefreshReuseRatherThanDefault(boolean persisted) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("RowGuid")).thenReturn(UUID.randomUUID().toString());
        when(row.getBoolean("ReuseRefreshTokens")).thenReturn(persisted);
        var detail = ApplicationDetail.fromResultSet(row);
        var json = new ObjectMapper().valueToTree(ApplicationDetailResponseDto.fromApplicationDetail(detail, UUID.randomUUID()));
        assertTrue(json.has("reuseRefreshTokens"));
        assertEquals(persisted, json.get("reuseRefreshTokens").booleanValue());
    }
}
