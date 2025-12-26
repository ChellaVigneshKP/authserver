package com.chellavignesh.authserver.mfa.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OtpGenerationFailedExceptionTest {

    @Test
    void testExceptionWithMessage() {
        String expectedMessage = "OTP generation failed";
        OtpGenerationFailedException exception = new OtpGenerationFailedException(expectedMessage);
        
        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    void testExceptionWithMessageAndCause() {
        String expectedMessage = "OTP generation failed";
        Exception cause = new RuntimeException("Service unavailable");
        OtpGenerationFailedException exception = new OtpGenerationFailedException(expectedMessage, cause);
        
        assertEquals(expectedMessage, exception.getMessage());
        assertNotNull(exception.getCause());
        assertEquals(cause, exception.getCause());
        assertEquals("Service unavailable", exception.getCause().getMessage());
    }

    @Test
    void testExceptionWithNullMessage() {
        OtpGenerationFailedException exception = new OtpGenerationFailedException(null);
        
        assertNull(exception.getMessage());
    }

    @Test
    void testExceptionWithNullCause() {
        String expectedMessage = "OTP generation failed";
        OtpGenerationFailedException exception = new OtpGenerationFailedException(expectedMessage, null);
        
        assertEquals(expectedMessage, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testExceptionWithEmptyMessage() {
        String expectedMessage = "";
        OtpGenerationFailedException exception = new OtpGenerationFailedException(expectedMessage);
        
        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    void testExceptionInheritance() {
        OtpGenerationFailedException exception = new OtpGenerationFailedException("test");
        
        assertTrue(exception instanceof Exception);
        assertTrue(exception instanceof Throwable);
    }

    @Test
    void testThrowExceptionWithMessage() {
        assertThrows(OtpGenerationFailedException.class, () -> {
            throw new OtpGenerationFailedException("Test exception");
        });
    }

    @Test
    void testThrowExceptionWithCause() {
        assertThrows(OtpGenerationFailedException.class, () -> {
            throw new OtpGenerationFailedException("Test exception", new RuntimeException());
        });
    }

    @Test
    void testExceptionCatchingWithMessage() {
        try {
            throw new OtpGenerationFailedException("Failed to generate OTP");
        } catch (OtpGenerationFailedException e) {
            assertEquals("Failed to generate OTP", e.getMessage());
        }
    }

    @Test
    void testExceptionCatchingWithCause() {
        try {
            Exception cause = new IllegalArgumentException("Invalid configuration");
            throw new OtpGenerationFailedException("OTP generation failed", cause);
        } catch (OtpGenerationFailedException e) {
            assertEquals("OTP generation failed", e.getMessage());
            assertNotNull(e.getCause());
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    void testCauseChaining() {
        Exception rootCause = new RuntimeException("Network error");
        Exception intermediateCause = new IllegalStateException("Service error", rootCause);
        OtpGenerationFailedException exception = new OtpGenerationFailedException("OTP failed", intermediateCause);
        
        assertEquals("OTP failed", exception.getMessage());
        assertEquals(intermediateCause, exception.getCause());
        assertEquals(rootCause, exception.getCause().getCause());
    }

    @Test
    void testMultipleLevelCauseChain() {
        Exception level3 = new RuntimeException("Level 3 error");
        Exception level2 = new IllegalStateException("Level 2 error", level3);
        Exception level1 = new IllegalArgumentException("Level 1 error", level2);
        OtpGenerationFailedException exception = new OtpGenerationFailedException("OTP generation failed", level1);
        
        assertNotNull(exception.getCause());
        assertNotNull(exception.getCause().getCause());
        assertNotNull(exception.getCause().getCause().getCause());
    }
}
