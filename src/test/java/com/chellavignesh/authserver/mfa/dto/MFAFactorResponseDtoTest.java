package com.chellavignesh.authserver.mfa.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MFAFactorResponseDtoTest {

    @Test
    void testNoArgsConstructor() {
        MFAFactorResponseDto dto = new MFAFactorResponseDto();
        
        assertNull(dto.getFactors());
        // Inherited from OtpResponseDto
        assertNull(dto.getStatus());
        assertNull(dto.getMessage());
        assertNull(dto.getUser_id());
    }

    @Test
    void testAllArgsConstructor() {
        List<MFAFactor> factors = Arrays.asList(
            new MFAFactor("sms", "sms1", "+1234567890", null, null),
            new MFAFactor("email", "email1", "test@example.com", null, null)
        );
        
        MFAFactorResponseDto dto = new MFAFactorResponseDto(factors);
        
        assertEquals(factors, dto.getFactors());
        assertEquals(2, dto.getFactors().size());
    }

    @Test
    void testSettersAndGetters() {
        MFAFactorResponseDto dto = new MFAFactorResponseDto();
        
        List<MFAFactor> factors = Arrays.asList(
            new MFAFactor("totp", "totp1", "secret", null, Arrays.asList("verify"))
        );
        
        dto.setFactors(factors);
        dto.setStatus("success");
        dto.setMessage("MFA factors retrieved");
        dto.setUser_id("user123");
        
        assertEquals(factors, dto.getFactors());
        assertEquals(1, dto.getFactors().size());
        assertEquals("success", dto.getStatus());
        assertEquals("MFA factors retrieved", dto.getMessage());
        assertEquals("user123", dto.getUser_id());
    }

    @Test
    void testEqualsAndHashCode() {
        List<MFAFactor> factors = Arrays.asList(
            new MFAFactor("sms", "sms1", "+1234567890", null, null)
        );
        
        MFAFactorResponseDto dto1 = new MFAFactorResponseDto(factors);
        MFAFactorResponseDto dto2 = new MFAFactorResponseDto(factors);
        MFAFactorResponseDto dto3 = new MFAFactorResponseDto(Collections.emptyList());
        
        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
        assertNotEquals(dto1, dto3);
    }

    @Test
    void testToString() {
        List<MFAFactor> factors = Arrays.asList(
            new MFAFactor("sms", "sms1", "+1234567890", null, null)
        );
        MFAFactorResponseDto dto = new MFAFactorResponseDto(factors);
        dto.setStatus("success");
        
        String toString = dto.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("success") || toString.contains("factors"));
    }

    @Test
    void testWithEmptyFactorsList() {
        MFAFactorResponseDto dto = new MFAFactorResponseDto(Collections.emptyList());
        
        assertNotNull(dto.getFactors());
        assertTrue(dto.getFactors().isEmpty());
    }

    @Test
    void testWithNullFactorsList() {
        MFAFactorResponseDto dto = new MFAFactorResponseDto(null);
        
        assertNull(dto.getFactors());
    }

    @Test
    void testWithMultipleFactors() {
        List<MFAFactor> factors = Arrays.asList(
            new MFAFactor("sms", "sms1", "+1234567890", null, Arrays.asList("send", "verify")),
            new MFAFactor("email", "email1", "test@example.com", null, Arrays.asList("send", "verify")),
            new MFAFactor("totp", "totp1", "secret", null, Arrays.asList("verify")),
            new MFAFactor("biometric", "bio1", "fingerprint", Arrays.asList("fingerprint", "face"), Arrays.asList("enroll", "verify"))
        );
        
        MFAFactorResponseDto dto = new MFAFactorResponseDto(factors);
        
        assertEquals(4, dto.getFactors().size());
        assertEquals("sms", dto.getFactors().get(0).getType());
        assertEquals("email", dto.getFactors().get(1).getType());
        assertEquals("totp", dto.getFactors().get(2).getType());
        assertEquals("biometric", dto.getFactors().get(3).getType());
    }

    @Test
    void testInheritanceFromOtpResponseDto() {
        MFAFactorResponseDto dto = new MFAFactorResponseDto();
        
        assertTrue(dto instanceof OtpResponseDto);
        
        dto.setStatus("success");
        dto.setMessage("Factors loaded");
        dto.setUser_id("user456");
        
        assertEquals("success", dto.getStatus());
        assertEquals("Factors loaded", dto.getMessage());
        assertEquals("user456", dto.getUser_id());
    }

    @Test
    void testModifyFactorsList() {
        MFAFactorResponseDto dto = new MFAFactorResponseDto();
        
        List<MFAFactor> factors = Arrays.asList(
            new MFAFactor("sms", "sms1", "+1234567890", null, null)
        );
        dto.setFactors(factors);
        
        assertEquals(1, dto.getFactors().size());
        
        List<MFAFactor> newFactors = Arrays.asList(
            new MFAFactor("sms", "sms1", "+1234567890", null, null),
            new MFAFactor("email", "email1", "test@example.com", null, null)
        );
        dto.setFactors(newFactors);
        
        assertEquals(2, dto.getFactors().size());
    }
}
