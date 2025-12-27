package com.chellavignesh.authserver.authcode.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthCodeCreationFailedExceptionTest {

    @Test
    void testExceptionMessage() {
        String expectedMessage = "Auth code creation failed";
        AuthCodeCreationFailedException exception = new AuthCodeCreationFailedException(expectedMessage);
        
        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    void testExceptionWithNullMessage() {
        AuthCodeCreationFailedException exception = new AuthCodeCreationFailedException(null);
        
        assertNull(exception.getMessage());
    }

    @Test
    void testExceptionWithEmptyMessage() {
        String expectedMessage = "";
        AuthCodeCreationFailedException exception = new AuthCodeCreationFailedException(expectedMessage);
        
        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    void testExceptionInheritance() {
        AuthCodeCreationFailedException exception = new AuthCodeCreationFailedException("test");
        
        assertTrue(exception instanceof Exception);
        assertTrue(exception instanceof Throwable);
    }

    @Test
    void testThrowException() {
        assertThrows(AuthCodeCreationFailedException.class, () -> {
            throw new AuthCodeCreationFailedException("Test exception");
        });
    }

    @Test
    void testExceptionCatching() {
        try {
            throw new AuthCodeCreationFailedException("Test message");
        } catch (AuthCodeCreationFailedException e) {
            assertEquals("Test message", e.getMessage());
        }
    }
}
