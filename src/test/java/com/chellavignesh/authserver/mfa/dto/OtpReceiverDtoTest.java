package com.chellavignesh.authserver.mfa.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OtpReceiverDtoTest {

    @Test
    void testConstructorAndGetters() {
        String userId = "user123";
        String type = "email";
        String factorId = "factor1";
        
        OtpReceiverDto dto = new OtpReceiverDto(userId, type, factorId);
        
        assertEquals(userId, dto.getUser_id());
        assertEquals(type, dto.getType());
        assertEquals(factorId, dto.getFactor_id());
        assertFalse(dto.isEvaluate_number());
    }

    @Test
    void testSetters() {
        OtpReceiverDto dto = new OtpReceiverDto("user1", "sms", "factor1");
        
        dto.setUser_id("user2");
        dto.setType("email");
        dto.setFactor_id("factor2");
        
        assertEquals("user2", dto.getUser_id());
        assertEquals("email", dto.getType());
        assertEquals("factor2", dto.getFactor_id());
    }

    @Test
    void testForSessionIdStaticMethod() {
        UUID sessionId = UUID.randomUUID();
        
        OtpReceiverDto dto = OtpReceiverDto.forSessionId(sessionId);
        
        assertNotNull(dto);
        assertEquals(sessionId.toString(), dto.getUser_id());
        assertEquals("sms", dto.getType());
        assertEquals("Phone1", dto.getFactor_id());
        assertFalse(dto.isEvaluate_number());
    }

    @Test
    void testEqualsAndHashCode() {
        OtpReceiverDto dto1 = new OtpReceiverDto("user1", "sms", "phone");
        OtpReceiverDto dto2 = new OtpReceiverDto("user1", "sms", "phone");
        OtpReceiverDto dto3 = new OtpReceiverDto("user2", "sms", "phone");
        
        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
        assertNotEquals(dto1, dto3);
    }

    @Test
    void testToString() {
        OtpReceiverDto dto = new OtpReceiverDto("testUser", "email", "Email1");
        
        String toString = dto.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("testUser") || toString.contains("user"));
    }

    @Test
    void testEvaluateNumberIsAlwaysFalse() {
        OtpReceiverDto dto1 = new OtpReceiverDto("user1", "sms", "phone");
        assertFalse(dto1.isEvaluate_number());
        
        UUID sessionId = UUID.randomUUID();
        OtpReceiverDto dto2 = OtpReceiverDto.forSessionId(sessionId);
        assertFalse(dto2.isEvaluate_number());
    }
}
