package com.chellavignesh.authserver.mfa.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OtpValidationRequestDtoTest {

    @Test
    void testConstructorAndGetters() {
        String userId = "user123";
        String otp = "123456";
        
        OtpValidationRequestDto dto = new OtpValidationRequestDto(userId, otp);
        
        assertEquals(userId, dto.getUser_id());
        assertEquals(otp, dto.getOtp());
    }

    @Test
    void testSetters() {
        OtpValidationRequestDto dto = new OtpValidationRequestDto("user1", "111111");
        
        dto.setUser_id("user2");
        dto.setOtp("222222");
        
        assertEquals("user2", dto.getUser_id());
        assertEquals("222222", dto.getOtp());
    }

    @Test
    void testEqualsAndHashCode() {
        OtpValidationRequestDto dto1 = new OtpValidationRequestDto("user1", "123456");
        OtpValidationRequestDto dto2 = new OtpValidationRequestDto("user1", "123456");
        OtpValidationRequestDto dto3 = new OtpValidationRequestDto("user2", "123456");
        
        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
        assertNotEquals(dto1, dto3);
    }

    @Test
    void testToString() {
        OtpValidationRequestDto dto = new OtpValidationRequestDto("testUser", "654321");
        
        String toString = dto.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("testUser") || toString.contains("user"));
        assertTrue(toString.contains("654321") || toString.contains("otp"));
    }

    @Test
    void testWithDifferentOtpFormats() {
        // 6-digit OTP
        OtpValidationRequestDto dto1 = new OtpValidationRequestDto("user", "123456");
        assertEquals("123456", dto1.getOtp());
        
        // 4-digit OTP
        OtpValidationRequestDto dto2 = new OtpValidationRequestDto("user", "1234");
        assertEquals("1234", dto2.getOtp());
        
        // 8-digit OTP
        OtpValidationRequestDto dto3 = new OtpValidationRequestDto("user", "12345678");
        assertEquals("12345678", dto3.getOtp());
    }
}
