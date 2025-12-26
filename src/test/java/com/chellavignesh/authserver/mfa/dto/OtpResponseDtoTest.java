package com.chellavignesh.authserver.mfa.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OtpResponseDtoTest {

    @Test
    void testNoArgsConstructor() {
        OtpResponseDto dto = new OtpResponseDto();
        
        assertNull(dto.getStatus());
        assertNull(dto.getMessage());
        assertNull(dto.getUser_id());
    }

    @Test
    void testAllArgsConstructor() {
        String status = "success";
        String message = "OTP sent successfully";
        String userId = "user123";
        
        OtpResponseDto dto = new OtpResponseDto(status, message, userId);
        
        assertEquals(status, dto.getStatus());
        assertEquals(message, dto.getMessage());
        assertEquals(userId, dto.getUser_id());
    }

    @Test
    void testSettersAndGetters() {
        OtpResponseDto dto = new OtpResponseDto();
        
        dto.setStatus("pending");
        dto.setMessage("OTP generation in progress");
        dto.setUser_id("user456");
        
        assertEquals("pending", dto.getStatus());
        assertEquals("OTP generation in progress", dto.getMessage());
        assertEquals("user456", dto.getUser_id());
    }

    @Test
    void testEqualsAndHashCode() {
        OtpResponseDto dto1 = new OtpResponseDto("success", "OTP sent", "user1");
        OtpResponseDto dto2 = new OtpResponseDto("success", "OTP sent", "user1");
        OtpResponseDto dto3 = new OtpResponseDto("failed", "OTP sent", "user1");
        
        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
        assertNotEquals(dto1, dto3);
    }

    @Test
    void testToString() {
        OtpResponseDto dto = new OtpResponseDto("success", "OTP verified", "testUser");
        
        String toString = dto.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("success"));
        assertTrue(toString.contains("testUser") || toString.contains("user"));
    }

    @Test
    void testSuccessResponse() {
        OtpResponseDto dto = new OtpResponseDto("success", "OTP sent successfully", "user789");
        
        assertEquals("success", dto.getStatus());
        assertNotNull(dto.getMessage());
        assertNotNull(dto.getUser_id());
    }

    @Test
    void testFailureResponse() {
        OtpResponseDto dto = new OtpResponseDto("failed", "Invalid OTP", "user789");
        
        assertEquals("failed", dto.getStatus());
        assertTrue(dto.getMessage().contains("Invalid"));
    }

    @Test
    void testWithNullValues() {
        OtpResponseDto dto = new OtpResponseDto(null, null, null);
        
        assertNull(dto.getStatus());
        assertNull(dto.getMessage());
        assertNull(dto.getUser_id());
    }

    @Test
    void testWithEmptyStrings() {
        OtpResponseDto dto = new OtpResponseDto("", "", "");
        
        assertEquals("", dto.getStatus());
        assertEquals("", dto.getMessage());
        assertEquals("", dto.getUser_id());
    }
}
