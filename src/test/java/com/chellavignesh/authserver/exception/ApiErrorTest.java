package com.chellavignesh.authserver.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class ApiErrorTest {

    @Test
    void testNoArgsConstructor() {
        ApiError apiError = new ApiError();
        
        assertNull(apiError.getStatus());
        assertNull(apiError.getMessage());
    }

    @Test
    void testAllArgsConstructor() {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String message = "Invalid request";
        
        ApiError apiError = new ApiError(status, message);
        
        assertEquals(status, apiError.getStatus());
        assertEquals(message, apiError.getMessage());
    }

    @Test
    void testSettersAndGetters() {
        ApiError apiError = new ApiError();
        
        apiError.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        apiError.setMessage("Server error occurred");
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, apiError.getStatus());
        assertEquals("Server error occurred", apiError.getMessage());
    }

    @Test
    void testEqualsAndHashCode() {
        ApiError error1 = new ApiError(HttpStatus.NOT_FOUND, "Not found");
        ApiError error2 = new ApiError(HttpStatus.NOT_FOUND, "Not found");
        ApiError error3 = new ApiError(HttpStatus.FORBIDDEN, "Forbidden");
        
        assertEquals(error1, error2);
        assertEquals(error1.hashCode(), error2.hashCode());
        assertNotEquals(error1, error3);
    }

    @Test
    void testToString() {
        ApiError apiError = new ApiError(HttpStatus.UNAUTHORIZED, "Unauthorized access");
        
        String toString = apiError.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("UNAUTHORIZED") || toString.contains("401"));
        assertTrue(toString.contains("Unauthorized access"));
    }

    @Test
    void testWithDifferentHttpStatuses() {
        ApiError error400 = new ApiError(HttpStatus.BAD_REQUEST, "Bad request");
        assertEquals(HttpStatus.BAD_REQUEST, error400.getStatus());
        
        ApiError error401 = new ApiError(HttpStatus.UNAUTHORIZED, "Unauthorized");
        assertEquals(HttpStatus.UNAUTHORIZED, error401.getStatus());
        
        ApiError error403 = new ApiError(HttpStatus.FORBIDDEN, "Forbidden");
        assertEquals(HttpStatus.FORBIDDEN, error403.getStatus());
        
        ApiError error404 = new ApiError(HttpStatus.NOT_FOUND, "Not found");
        assertEquals(HttpStatus.NOT_FOUND, error404.getStatus());
        
        ApiError error500 = new ApiError(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error500.getStatus());
    }

    @Test
    void testWithNullValues() {
        ApiError apiError = new ApiError(null, null);
        
        assertNull(apiError.getStatus());
        assertNull(apiError.getMessage());
    }

    @Test
    void testWithEmptyMessage() {
        ApiError apiError = new ApiError(HttpStatus.OK, "");
        
        assertEquals(HttpStatus.OK, apiError.getStatus());
        assertEquals("", apiError.getMessage());
    }

    @Test
    void testUpdateStatus() {
        ApiError apiError = new ApiError(HttpStatus.OK, "Success");
        
        apiError.setStatus(HttpStatus.CREATED);
        
        assertEquals(HttpStatus.CREATED, apiError.getStatus());
        assertEquals("Success", apiError.getMessage());
    }

    @Test
    void testUpdateMessage() {
        ApiError apiError = new ApiError(HttpStatus.BAD_REQUEST, "Original message");
        
        apiError.setMessage("Updated message");
        
        assertEquals(HttpStatus.BAD_REQUEST, apiError.getStatus());
        assertEquals("Updated message", apiError.getMessage());
    }
}
