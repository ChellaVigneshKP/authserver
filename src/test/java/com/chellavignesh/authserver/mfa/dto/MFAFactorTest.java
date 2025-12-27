package com.chellavignesh.authserver.mfa.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MFAFactorTest {

    @Test
    void testNoArgsConstructor() {
        MFAFactor factor = new MFAFactor();
        
        assertNull(factor.getType());
        assertNull(factor.getId());
        assertNull(factor.getValue());
        assertNull(factor.getBiometricTypes());
        assertNull(factor.getCapabilities());
    }

    @Test
    void testAllArgsConstructor() {
        String type = "biometric";
        String id = "factor1";
        String value = "fingerprint";
        List<String> biometricTypes = Arrays.asList("fingerprint", "face");
        List<String> capabilities = Arrays.asList("verify", "enroll");
        
        MFAFactor factor = new MFAFactor(type, id, value, biometricTypes, capabilities);
        
        assertEquals(type, factor.getType());
        assertEquals(id, factor.getId());
        assertEquals(value, factor.getValue());
        assertEquals(biometricTypes, factor.getBiometricTypes());
        assertEquals(capabilities, factor.getCapabilities());
    }

    @Test
    void testSettersAndGetters() {
        MFAFactor factor = new MFAFactor();
        
        factor.setType("sms");
        factor.setId("sms1");
        factor.setValue("+1234567890");
        factor.setBiometricTypes(Collections.emptyList());
        factor.setCapabilities(Arrays.asList("send", "verify"));
        
        assertEquals("sms", factor.getType());
        assertEquals("sms1", factor.getId());
        assertEquals("+1234567890", factor.getValue());
        assertTrue(factor.getBiometricTypes().isEmpty());
        assertEquals(2, factor.getCapabilities().size());
    }

    @Test
    void testEqualsAndHashCode() {
        List<String> biometricTypes = Arrays.asList("fingerprint");
        List<String> capabilities = Arrays.asList("verify");
        
        MFAFactor factor1 = new MFAFactor("type1", "id1", "value1", biometricTypes, capabilities);
        MFAFactor factor2 = new MFAFactor("type1", "id1", "value1", biometricTypes, capabilities);
        MFAFactor factor3 = new MFAFactor("type2", "id1", "value1", biometricTypes, capabilities);
        
        assertEquals(factor1, factor2);
        assertEquals(factor1.hashCode(), factor2.hashCode());
        assertNotEquals(factor1, factor3);
    }

    @Test
    void testToString() {
        MFAFactor factor = new MFAFactor("email", "email1", "test@example.com", null, null);
        
        String toString = factor.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("email"));
        assertTrue(toString.contains("email1"));
        assertTrue(toString.contains("test@example.com"));
    }

    @Test
    void testWithNullLists() {
        MFAFactor factor = new MFAFactor("sms", "sms1", "+1234567890", null, null);
        
        assertNull(factor.getBiometricTypes());
        assertNull(factor.getCapabilities());
    }

    @Test
    void testWithEmptyLists() {
        MFAFactor factor = new MFAFactor("totp", "totp1", "secret", Collections.emptyList(), Collections.emptyList());
        
        assertNotNull(factor.getBiometricTypes());
        assertTrue(factor.getBiometricTypes().isEmpty());
        assertNotNull(factor.getCapabilities());
        assertTrue(factor.getCapabilities().isEmpty());
    }

    @Test
    void testWithMultipleBiometricTypes() {
        List<String> biometricTypes = Arrays.asList("fingerprint", "face", "iris", "voice");
        MFAFactor factor = new MFAFactor("biometric", "bio1", "multi", biometricTypes, null);
        
        assertEquals(4, factor.getBiometricTypes().size());
        assertTrue(factor.getBiometricTypes().contains("fingerprint"));
        assertTrue(factor.getBiometricTypes().contains("iris"));
    }
}
