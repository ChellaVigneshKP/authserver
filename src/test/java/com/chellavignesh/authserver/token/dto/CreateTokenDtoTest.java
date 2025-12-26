package com.chellavignesh.authserver.token.dto;

import com.chellavignesh.authserver.enums.entity.TokenTypeEnum;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CreateTokenDtoTest {

    @Test
    void testNoArgsConstructor() {
        CreateTokenDto dto = new CreateTokenDto();
        
        assertNull(dto.getTokenType());
        assertNull(dto.getSubjectId());
        assertNull(dto.getSessionId());
        assertNull(dto.getApplicationId());
        assertNull(dto.getData());
        assertFalse(dto.isOpaque());
        assertNull(dto.getSigningKey());
        assertNull(dto.getTimeToLive());
    }

    @Test
    void testAllArgsConstructor() {
        TokenTypeEnum tokenType = TokenTypeEnum.ACCESS_TOKEN;
        String subjectId = "user123";
        UUID sessionId = UUID.randomUUID();
        Integer applicationId = 456;
        String data = "token-data";
        boolean isOpaque = true;
        SecretKey signingKey = new SecretKeySpec("secret".getBytes(), "HmacSHA256");
        Integer timeToLive = 3600;
        
        CreateTokenDto dto = new CreateTokenDto(tokenType, subjectId, sessionId, applicationId, 
                                                 data, isOpaque, signingKey, timeToLive);
        
        assertEquals(tokenType, dto.getTokenType());
        assertEquals(subjectId, dto.getSubjectId());
        assertEquals(sessionId, dto.getSessionId());
        assertEquals(applicationId, dto.getApplicationId());
        assertEquals(data, dto.getData());
        assertTrue(dto.isOpaque());
        assertEquals(signingKey, dto.getSigningKey());
        assertEquals(timeToLive, dto.getTimeToLive());
    }

    @Test
    void testSettersAndGetters() {
        CreateTokenDto dto = new CreateTokenDto();
        TokenTypeEnum tokenType = TokenTypeEnum.REFRESH_TOKEN;
        String subjectId = "subject789";
        UUID sessionId = UUID.randomUUID();
        Integer applicationId = 789;
        String data = "refresh-data";
        SecretKey signingKey = new SecretKeySpec("key".getBytes(), "HmacSHA256");
        Integer timeToLive = 7200;
        
        dto.setTokenType(tokenType);
        dto.setSubjectId(subjectId);
        dto.setSessionId(sessionId);
        dto.setApplicationId(applicationId);
        dto.setData(data);
        dto.setOpaque(false);
        dto.setSigningKey(signingKey);
        dto.setTimeToLive(timeToLive);
        
        assertEquals(tokenType, dto.getTokenType());
        assertEquals(subjectId, dto.getSubjectId());
        assertEquals(sessionId, dto.getSessionId());
        assertEquals(applicationId, dto.getApplicationId());
        assertEquals(data, dto.getData());
        assertFalse(dto.isOpaque());
        assertEquals(signingKey, dto.getSigningKey());
        assertEquals(timeToLive, dto.getTimeToLive());
    }

    @Test
    void testEqualsAndHashCode() {
        UUID sessionId = UUID.randomUUID();
        SecretKey key = new SecretKeySpec("key".getBytes(), "HmacSHA256");
        
        CreateTokenDto dto1 = new CreateTokenDto(TokenTypeEnum.ACCESS_TOKEN, "user1", sessionId, 123, 
                                                  "data", true, key, 3600);
        CreateTokenDto dto2 = new CreateTokenDto(TokenTypeEnum.ACCESS_TOKEN, "user1", sessionId, 123, 
                                                  "data", true, key, 3600);
        CreateTokenDto dto3 = new CreateTokenDto(TokenTypeEnum.REFRESH_TOKEN, "user1", sessionId, 123, 
                                                  "data", true, key, 3600);
        
        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
        assertNotEquals(dto1, dto3);
    }

    @Test
    void testToString() {
        CreateTokenDto dto = new CreateTokenDto(TokenTypeEnum.ACCESS_TOKEN, "user", UUID.randomUUID(), 
                                                 123, "data", true, null, 3600);
        
        String toString = dto.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("user") || toString.contains("subject"));
        assertTrue(toString.contains("123"));
    }

    @Test
    void testOpaqueFlag() {
        CreateTokenDto dto = new CreateTokenDto();
        
        assertFalse(dto.isOpaque());
        
        dto.setOpaque(true);
        assertTrue(dto.isOpaque());
        
        dto.setOpaque(false);
        assertFalse(dto.isOpaque());
    }

    @Test
    void testWithNullValues() {
        CreateTokenDto dto = new CreateTokenDto(null, null, null, null, null, false, null, null);
        
        assertNull(dto.getTokenType());
        assertNull(dto.getSubjectId());
        assertNull(dto.getSessionId());
        assertNull(dto.getApplicationId());
        assertNull(dto.getData());
        assertFalse(dto.isOpaque());
        assertNull(dto.getSigningKey());
        assertNull(dto.getTimeToLive());
    }

    @Test
    void testTimeToLiveValues() {
        CreateTokenDto dto = new CreateTokenDto();
        
        // Short TTL
        dto.setTimeToLive(60);
        assertEquals(60, dto.getTimeToLive());
        
        // Standard TTL (1 hour)
        dto.setTimeToLive(3600);
        assertEquals(3600, dto.getTimeToLive());
        
        // Long TTL (30 days)
        dto.setTimeToLive(2592000);
        assertEquals(2592000, dto.getTimeToLive());
        
        // Zero TTL
        dto.setTimeToLive(0);
        assertEquals(0, dto.getTimeToLive());
    }

    @Test
    void testDifferentTokenTypes() {
        CreateTokenDto accessToken = new CreateTokenDto();
        accessToken.setTokenType(TokenTypeEnum.ACCESS_TOKEN);
        assertEquals(TokenTypeEnum.ACCESS_TOKEN, accessToken.getTokenType());
        
        CreateTokenDto refreshToken = new CreateTokenDto();
        refreshToken.setTokenType(TokenTypeEnum.REFRESH_TOKEN);
        assertEquals(TokenTypeEnum.REFRESH_TOKEN, refreshToken.getTokenType());
        
        CreateTokenDto idToken = new CreateTokenDto();
        idToken.setTokenType(TokenTypeEnum.ID_TOKEN);
        assertEquals(TokenTypeEnum.ID_TOKEN, idToken.getTokenType());
    }

    @Test
    void testWithDifferentSecretKeys() {
        CreateTokenDto dto = new CreateTokenDto();
        
        SecretKey hmacKey = new SecretKeySpec("hmac-secret".getBytes(), "HmacSHA256");
        dto.setSigningKey(hmacKey);
        assertEquals(hmacKey, dto.getSigningKey());
        assertEquals("HmacSHA256", dto.getSigningKey().getAlgorithm());
        
        SecretKey aesKey = new SecretKeySpec("aes-secret-key".getBytes(), "AES");
        dto.setSigningKey(aesKey);
        assertEquals(aesKey, dto.getSigningKey());
        assertEquals("AES", dto.getSigningKey().getAlgorithm());
    }

    @Test
    void testEmptyStringData() {
        CreateTokenDto dto = new CreateTokenDto();
        dto.setData("");
        dto.setSubjectId("");
        
        assertEquals("", dto.getData());
        assertEquals("", dto.getSubjectId());
    }

    @Test
    void testZeroApplicationId() {
        CreateTokenDto dto = new CreateTokenDto();
        dto.setApplicationId(0);
        
        assertEquals(0, dto.getApplicationId());
    }

    @Test
    void testNegativeValues() {
        CreateTokenDto dto = new CreateTokenDto();
        
        dto.setApplicationId(-1);
        dto.setTimeToLive(-100);
        
        assertEquals(-1, dto.getApplicationId());
        assertEquals(-100, dto.getTimeToLive());
    }
}
