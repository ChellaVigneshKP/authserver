package com.chellavignesh.authserver.session.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogoutRequestTest {

    @Test
    void testRecordConstructorAndGetters() {
        String idTokenHint = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...";
        String clientId = "client-123";
        String postLogoutRedirectUri = "https://example.com/logout";
        String errorCode = null;
        
        LogoutRequest request = new LogoutRequest(idTokenHint, clientId, postLogoutRedirectUri, errorCode);
        
        assertEquals(idTokenHint, request.id_token_hint());
        assertEquals(clientId, request.client_id());
        assertEquals(postLogoutRedirectUri, request.post_logout_redirect_uri());
        assertNull(request.error_code());
    }

    @Test
    void testRecordWithErrorCode() {
        String idTokenHint = "token";
        String clientId = "client-456";
        String postLogoutRedirectUri = "https://example.com/logout";
        String errorCode = "invalid_token";
        
        LogoutRequest request = new LogoutRequest(idTokenHint, clientId, postLogoutRedirectUri, errorCode);
        
        assertEquals(idTokenHint, request.id_token_hint());
        assertEquals(clientId, request.client_id());
        assertEquals(postLogoutRedirectUri, request.post_logout_redirect_uri());
        assertEquals(errorCode, request.error_code());
    }

    @Test
    void testEquality() {
        LogoutRequest request1 = new LogoutRequest("token1", "client1", "https://example.com", "error1");
        LogoutRequest request2 = new LogoutRequest("token1", "client1", "https://example.com", "error1");
        LogoutRequest request3 = new LogoutRequest("token2", "client1", "https://example.com", "error1");
        
        assertEquals(request1, request2);
        assertNotEquals(request1, request3);
    }

    @Test
    void testHashCode() {
        LogoutRequest request1 = new LogoutRequest("token1", "client1", "https://example.com", null);
        LogoutRequest request2 = new LogoutRequest("token1", "client1", "https://example.com", null);
        
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void testToString() {
        LogoutRequest request = new LogoutRequest("token", "client", "https://example.com", "error");
        
        String toString = request.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("token"));
        assertTrue(toString.contains("client"));
        assertTrue(toString.contains("https://example.com"));
        assertTrue(toString.contains("error"));
    }

    @Test
    void testWithNullErrorCode() {
        LogoutRequest request = new LogoutRequest("token", "client", "uri", null);
        
        assertNotNull(request.id_token_hint());
        assertNotNull(request.client_id());
        assertNotNull(request.post_logout_redirect_uri());
        assertNull(request.error_code());
    }

    @Test
    void testWithEmptyStrings() {
        LogoutRequest request = new LogoutRequest("", "", "", "");
        
        assertEquals("", request.id_token_hint());
        assertEquals("", request.client_id());
        assertEquals("", request.post_logout_redirect_uri());
        assertEquals("", request.error_code());
    }

    @Test
    void testWithLongTokenHint() {
        String longToken = "a".repeat(2000);
        LogoutRequest request = new LogoutRequest(longToken, "client", "uri", null);
        
        assertEquals(longToken, request.id_token_hint());
        assertEquals(2000, request.id_token_hint().length());
    }

    @Test
    void testWithVariousErrorCodes() {
        LogoutRequest request1 = new LogoutRequest("token", "client", "uri", "invalid_token");
        assertEquals("invalid_token", request1.error_code());
        
        LogoutRequest request2 = new LogoutRequest("token", "client", "uri", "server_error");
        assertEquals("server_error", request2.error_code());
        
        LogoutRequest request3 = new LogoutRequest("token", "client", "uri", "access_denied");
        assertEquals("access_denied", request3.error_code());
    }

    @Test
    void testImmutability() {
        LogoutRequest request = new LogoutRequest("token", "client", "uri", "error");
        
        // Records are immutable - values cannot be changed after construction
        assertEquals("token", request.id_token_hint());
        assertEquals("client", request.client_id());
        assertEquals("uri", request.post_logout_redirect_uri());
        assertEquals("error", request.error_code());
    }
}
