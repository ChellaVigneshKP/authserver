package com.chellavignesh.authserver.mfa.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OtpValidationFailedExceptionTest {

    @Test
    void testExceptionWithMessage() {
        String expectedMessage = "OTP validation failed";
        OtpValidationFailedException exception = new OtpValidationFailedException(expectedMessage);
        
        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    void testExceptionWithMessageAndCause() {
        String expectedMessage = "OTP validation failed";
        Exception cause = new RuntimeException("Underlying error");
        OtpValidationFailedException exception = new OtpValidationFailedException(expectedMessage, cause);
        
        assertEquals(expectedMessage, exception.getMessage());
        assertNotNull(exception.getCause());
        assertEquals(cause, exception.getCause());
        assertEquals("Underlying error", exception.getCause().getMessage());
    }

    @Test
    void testExceptionWithNullMessage() {
        OtpValidationFailedException exception = new OtpValidationFailedException(null);
        
        assertNull(exception.getMessage());
    }

    @Test
    void testExceptionWithNullCause() {
        String expectedMessage = "OTP validation failed";
        OtpValidationFailedException exception = new OtpValidationFailedException(expectedMessage, null);
        
        assertEquals(expectedMessage, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testExceptionWithEmptyMessage() {
        String expectedMessage = "";
        OtpValidationFailedException exception = new OtpValidationFailedException(expectedMessage);
        
        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    void testExceptionInheritance() {
        OtpValidationFailedException exception = new OtpValidationFailedException("test");
        
        assertTrue(exception instanceof Exception);
        assertTrue(exception instanceof Throwable);
    }

    @Test
    void testThrowExceptionWithMessage() {
        assertThrows(OtpValidationFailedException.class, () -> {
            throw new OtpValidationFailedException("Test exception");
        });
    }

    @Test
    void testThrowExceptionWithCause() {
        assertThrows(OtpValidationFailedException.class, () -> {
            throw new OtpValidationFailedException("Test exception", new RuntimeException());
        });
    }

    @Test
    void testExceptionCatchingWithMessage() {
        try {
            throw new OtpValidationFailedException("OTP is invalid");
        } catch (OtpValidationFailedException e) {
            assertEquals("OTP is invalid", e.getMessage());
        }
    }

    @Test
    void testExceptionCatchingWithCause() {
        try {
            Exception cause = new IllegalArgumentException("Invalid argument");
            throw new OtpValidationFailedException("OTP validation failed", cause);
        } catch (OtpValidationFailedException e) {
            assertEquals("OTP validation failed", e.getMessage());
            assertNotNull(e.getCause());
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    void testCauseChaining() {
        Exception rootCause = new RuntimeException("Root cause");
        Exception intermediateCause = new IllegalStateException("Intermediate cause", rootCause);
        OtpValidationFailedException exception = new OtpValidationFailedException("OTP failed", intermediateCause);
        
        assertEquals("OTP failed", exception.getMessage());
        assertEquals(intermediateCause, exception.getCause());
        assertEquals(rootCause, exception.getCause().getCause());
    }
}
