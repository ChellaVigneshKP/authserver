package com.chellavignesh.authserver.authcode.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CreateAuthCodeDtoTest {

    @Test
    void testConstructorAndGetters() {
        CreateAuthCodeDto dto = new CreateAuthCodeDto();
        assertNull(dto.getApplicationId());
        assertNull(dto.getSessionId());
        assertNull(dto.getData());
    }

    @Test
    void testSettersAndGetters() {
        CreateAuthCodeDto dto = new CreateAuthCodeDto();
        Integer appId = 123;
        UUID sessionId = UUID.randomUUID();
        String data = "test-data";

        dto.setApplicationId(appId);
        dto.setSessionId(sessionId);
        dto.setData(data);

        assertEquals(appId, dto.getApplicationId());
        assertEquals(sessionId, dto.getSessionId());
        assertEquals(data, dto.getData());
    }

    @Test
    void testEqualsAndHashCode() {
        UUID sessionId = UUID.randomUUID();
        CreateAuthCodeDto dto1 = new CreateAuthCodeDto();
        dto1.setApplicationId(123);
        dto1.setSessionId(sessionId);
        dto1.setData("test");

        CreateAuthCodeDto dto2 = new CreateAuthCodeDto();
        dto2.setApplicationId(123);
        dto2.setSessionId(sessionId);
        dto2.setData("test");

        CreateAuthCodeDto dto3 = new CreateAuthCodeDto();
        dto3.setApplicationId(456);
        dto3.setSessionId(sessionId);
        dto3.setData("test");

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
        assertNotEquals(dto1, dto3);
    }

    @Test
    void testToString() {
        CreateAuthCodeDto dto = new CreateAuthCodeDto();
        dto.setApplicationId(123);
        dto.setSessionId(UUID.randomUUID());
        dto.setData("test");

        String toString = dto.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("123"));
        assertTrue(toString.contains("test"));
    }

    @Test
    void testNullValues() {
        CreateAuthCodeDto dto = new CreateAuthCodeDto();
        dto.setApplicationId(null);
        dto.setSessionId(null);
        dto.setData(null);

        assertNull(dto.getApplicationId());
        assertNull(dto.getSessionId());
        assertNull(dto.getData());
    }
}
