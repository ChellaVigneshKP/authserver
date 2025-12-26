package com.chellavignesh.authserver.authcode.entity;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthCodeTest {

    @Test
    void testNoArgsConstructor() {
        AuthCode authCode = new AuthCode();
        
        assertNull(authCode.getId());
        assertNull(authCode.getApplicationId());
        assertNull(authCode.getSessionId());
        assertNull(authCode.getData());
        assertNull(authCode.getConsumedOn());
    }

    @Test
    void testAllArgsConstructor() {
        Integer id = 1;
        Integer applicationId = 123;
        UUID sessionId = UUID.randomUUID();
        String data = "auth-code-data";
        Instant consumedOn = Instant.now();
        
        AuthCode authCode = new AuthCode(id, applicationId, sessionId, data, consumedOn);
        
        assertEquals(id, authCode.getId());
        assertEquals(applicationId, authCode.getApplicationId());
        assertEquals(sessionId, authCode.getSessionId());
        assertEquals(data, authCode.getData());
        assertEquals(consumedOn, authCode.getConsumedOn());
    }

    @Test
    void testSettersAndGetters() {
        AuthCode authCode = new AuthCode();
        Integer id = 10;
        Integer applicationId = 456;
        UUID sessionId = UUID.randomUUID();
        String data = "test-data";
        Instant consumedOn = Instant.now();
        
        authCode.setId(id);
        authCode.setApplicationId(applicationId);
        authCode.setSessionId(sessionId);
        authCode.setData(data);
        authCode.setConsumedOn(consumedOn);
        
        assertEquals(id, authCode.getId());
        assertEquals(applicationId, authCode.getApplicationId());
        assertEquals(sessionId, authCode.getSessionId());
        assertEquals(data, authCode.getData());
        assertEquals(consumedOn, authCode.getConsumedOn());
    }

    @Test
    void testEqualsAndHashCode() {
        UUID sessionId = UUID.randomUUID();
        Instant consumedOn = Instant.now();
        
        AuthCode authCode1 = new AuthCode(1, 123, sessionId, "data", consumedOn);
        AuthCode authCode2 = new AuthCode(1, 123, sessionId, "data", consumedOn);
        AuthCode authCode3 = new AuthCode(2, 123, sessionId, "data", consumedOn);
        
        assertEquals(authCode1, authCode2);
        assertEquals(authCode1.hashCode(), authCode2.hashCode());
        assertNotEquals(authCode1, authCode3);
    }

    @Test
    void testToString() {
        AuthCode authCode = new AuthCode(1, 123, UUID.randomUUID(), "test-data", Instant.now());
        
        String toString = authCode.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("1"));
        assertTrue(toString.contains("123"));
        assertTrue(toString.contains("test-data"));
    }

    @Test
    void testFromResult_Success() throws SQLException {
        ResultSet rs = Mockito.mock(ResultSet.class);
        UUID sessionId = UUID.randomUUID();
        
        when(rs.getInt("AuthCodeId")).thenReturn(1);
        when(rs.getInt("ApplicationId")).thenReturn(123);
        when(rs.getString("SessionId")).thenReturn(sessionId.toString());
        when(rs.getString("Data")).thenReturn("auth-code-data");
        when(rs.getInt("ConsumedOn")).thenReturn(1000);
        
        AuthCode authCode = AuthCode.fromResult(rs);
        
        assertNotNull(authCode);
        assertEquals(1, authCode.getId());
        assertEquals(123, authCode.getApplicationId());
        assertEquals(sessionId, authCode.getSessionId());
        assertEquals("auth-code-data", authCode.getData());
        assertNotNull(authCode.getConsumedOn());
    }

    @Test
    void testFromResult_SQLException() throws SQLException {
        ResultSet rs = Mockito.mock(ResultSet.class);
        
        when(rs.getInt("AuthCodeId")).thenThrow(new SQLException("Database error"));
        
        AuthCode authCode = AuthCode.fromResult(rs);
        
        assertNull(authCode);
    }

    @Test
    void testNullValues() {
        AuthCode authCode = new AuthCode(null, null, null, null, null);
        
        assertNull(authCode.getId());
        assertNull(authCode.getApplicationId());
        assertNull(authCode.getSessionId());
        assertNull(authCode.getData());
        assertNull(authCode.getConsumedOn());
    }

    @Test
    void testWithZeroValues() {
        AuthCode authCode = new AuthCode();
        authCode.setId(0);
        authCode.setApplicationId(0);
        
        assertEquals(0, authCode.getId());
        assertEquals(0, authCode.getApplicationId());
    }

    @Test
    void testWithNegativeValues() {
        AuthCode authCode = new AuthCode();
        authCode.setId(-1);
        authCode.setApplicationId(-100);
        
        assertEquals(-1, authCode.getId());
        assertEquals(-100, authCode.getApplicationId());
    }

    @Test
    void testConsumedOnInstant() {
        AuthCode authCode = new AuthCode();
        Instant now = Instant.now();
        Instant past = Instant.ofEpochMilli(1000000);
        Instant future = Instant.ofEpochMilli(System.currentTimeMillis() + 100000);
        
        authCode.setConsumedOn(now);
        assertEquals(now, authCode.getConsumedOn());
        
        authCode.setConsumedOn(past);
        assertEquals(past, authCode.getConsumedOn());
        
        authCode.setConsumedOn(future);
        assertEquals(future, authCode.getConsumedOn());
    }

    @Test
    void testEmptyStringData() {
        AuthCode authCode = new AuthCode();
        authCode.setData("");
        
        assertEquals("", authCode.getData());
    }

    @Test
    void testLongStringData() {
        AuthCode authCode = new AuthCode();
        String longData = "a".repeat(1000);
        authCode.setData(longData);
        
        assertEquals(longData, authCode.getData());
        assertEquals(1000, authCode.getData().length());
    }
}
