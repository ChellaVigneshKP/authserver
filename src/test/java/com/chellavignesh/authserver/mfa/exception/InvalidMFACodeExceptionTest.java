package com.chellavignesh.authserver.mfa.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvalidMFACodeExceptionTest {

    @Test
    void testExceptionMessage() {
        String expectedMessage = "Invalid MFA code";
        InvalidMFACodeException exception = new InvalidMFACodeException(expectedMessage);
        
        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    void testExceptionWithNullMessage() {
        InvalidMFACodeException exception = new InvalidMFACodeException(null);
        
        assertNull(exception.getMessage());
    }

    @Test
    void testExceptionWithEmptyMessage() {
        String expectedMessage = "";
        InvalidMFACodeException exception = new InvalidMFACodeException(expectedMessage);
        
        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    void testExceptionInheritance() {
        InvalidMFACodeException exception = new InvalidMFACodeException("test");
        
        assertTrue(exception instanceof Exception);
        assertTrue(exception instanceof Throwable);
    }

    @Test
    void testThrowException() {
        assertThrows(InvalidMFACodeException.class, () -> {
            throw new InvalidMFACodeException("Test exception");
        });
    }

    @Test
    void testExceptionCatching() {
        try {
            throw new InvalidMFACodeException("MFA code is invalid");
        } catch (InvalidMFACodeException e) {
            assertEquals("MFA code is invalid", e.getMessage());
        }
    }

    @Test
    void testExceptionWithLongMessage() {
        String longMessage = "This is a very long error message ".repeat(10);
        InvalidMFACodeException exception = new InvalidMFACodeException(longMessage);
        
        assertEquals(longMessage, exception.getMessage());
    }
}
