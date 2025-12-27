package com.chellavignesh.authserver.authcode;

import com.chellavignesh.authserver.authcode.dto.CreateAuthCodeDto;
import com.chellavignesh.authserver.authcode.entity.AuthCode;
import com.chellavignesh.authserver.authcode.exception.AuthCodeCreationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthCodeServiceTest {

    @Mock
    private AuthCodeRepository authCodeRepository;

    @InjectMocks
    private AuthCodeService authCodeService;

    private CreateAuthCodeDto createAuthCodeDto;
    private AuthCode authCode;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        createAuthCodeDto = new CreateAuthCodeDto();
        createAuthCodeDto.setApplicationId(123);
        createAuthCodeDto.setSessionId(sessionId);
        createAuthCodeDto.setData("test-auth-code");

        authCode = new AuthCode();
        authCode.setId(1);
        authCode.setApplicationId(123);
        authCode.setSessionId(sessionId);
        authCode.setData("test-auth-code");
    }

    @Test
    void testCreate_Success() throws AuthCodeCreationFailedException {
        when(authCodeRepository.create(any(CreateAuthCodeDto.class))).thenReturn(authCode);

        AuthCode result = authCodeService.create(createAuthCodeDto);

        assertNotNull(result);
        assertEquals(authCode.getId(), result.getId());
        assertEquals(authCode.getApplicationId(), result.getApplicationId());
        assertEquals(authCode.getSessionId(), result.getSessionId());
        assertEquals(authCode.getData(), result.getData());
        verify(authCodeRepository, times(1)).create(createAuthCodeDto);
    }

    @Test
    void testCreate_ThrowsException() throws AuthCodeCreationFailedException {
        when(authCodeRepository.create(any(CreateAuthCodeDto.class)))
            .thenThrow(new AuthCodeCreationFailedException("Failed to create auth code"));

        assertThrows(AuthCodeCreationFailedException.class, () -> {
            authCodeService.create(createAuthCodeDto);
        });
        verify(authCodeRepository, times(1)).create(createAuthCodeDto);
    }

    @Test
    void testGetSessionIdByAuthCode_Found() {
        String authCodeData = "test-auth-code";
        when(authCodeRepository.getSessionIdByAuthCode(authCodeData)).thenReturn(Optional.of(sessionId));

        Optional<UUID> result = authCodeService.getSessionIdByAuthCode(authCodeData);

        assertTrue(result.isPresent());
        assertEquals(sessionId, result.get());
        verify(authCodeRepository, times(1)).getSessionIdByAuthCode(authCodeData);
    }

    @Test
    void testGetSessionIdByAuthCode_NotFound() {
        String authCodeData = "non-existent-code";
        when(authCodeRepository.getSessionIdByAuthCode(authCodeData)).thenReturn(Optional.empty());

        Optional<UUID> result = authCodeService.getSessionIdByAuthCode(authCodeData);

        assertFalse(result.isPresent());
        verify(authCodeRepository, times(1)).getSessionIdByAuthCode(authCodeData);
    }

    @Test
    void testGetSessionIdByAuthCode_WithNullAuthCode() {
        when(authCodeRepository.getSessionIdByAuthCode(null)).thenReturn(Optional.empty());

        Optional<UUID> result = authCodeService.getSessionIdByAuthCode(null);

        assertFalse(result.isPresent());
        verify(authCodeRepository, times(1)).getSessionIdByAuthCode(null);
    }

    @Test
    void testGetSessionIdByAuthCode_WithEmptyAuthCode() {
        when(authCodeRepository.getSessionIdByAuthCode("")).thenReturn(Optional.empty());

        Optional<UUID> result = authCodeService.getSessionIdByAuthCode("");

        assertFalse(result.isPresent());
        verify(authCodeRepository, times(1)).getSessionIdByAuthCode("");
    }

    @Test
    void testSetConsumedOn_Success() {
        String authCodeData = "test-auth-code";
        doNothing().when(authCodeRepository).setConsumedOn(authCodeData);

        authCodeService.setConsumedOn(authCodeData);

        verify(authCodeRepository, times(1)).setConsumedOn(authCodeData);
    }

    @Test
    void testSetConsumedOn_WithNullAuthCode() {
        doNothing().when(authCodeRepository).setConsumedOn(null);

        authCodeService.setConsumedOn(null);

        verify(authCodeRepository, times(1)).setConsumedOn(null);
    }

    @Test
    void testSetConsumedOn_WithEmptyAuthCode() {
        doNothing().when(authCodeRepository).setConsumedOn("");

        authCodeService.setConsumedOn("");

        verify(authCodeRepository, times(1)).setConsumedOn("");
    }

    @Test
    void testSetConsumedOn_MultipleCalls() {
        String authCodeData = "test-auth-code";
        doNothing().when(authCodeRepository).setConsumedOn(authCodeData);

        authCodeService.setConsumedOn(authCodeData);
        authCodeService.setConsumedOn(authCodeData);
        authCodeService.setConsumedOn(authCodeData);

        verify(authCodeRepository, times(3)).setConsumedOn(authCodeData);
    }

    @Test
    void testCreateWithDifferentApplicationIds() throws AuthCodeCreationFailedException {
        CreateAuthCodeDto dto1 = new CreateAuthCodeDto();
        dto1.setApplicationId(100);
        dto1.setSessionId(UUID.randomUUID());
        dto1.setData("code1");

        CreateAuthCodeDto dto2 = new CreateAuthCodeDto();
        dto2.setApplicationId(200);
        dto2.setSessionId(UUID.randomUUID());
        dto2.setData("code2");

        AuthCode authCode1 = new AuthCode();
        authCode1.setId(1);
        authCode1.setApplicationId(100);

        AuthCode authCode2 = new AuthCode();
        authCode2.setId(2);
        authCode2.setApplicationId(200);

        when(authCodeRepository.create(dto1)).thenReturn(authCode1);
        when(authCodeRepository.create(dto2)).thenReturn(authCode2);

        AuthCode result1 = authCodeService.create(dto1);
        AuthCode result2 = authCodeService.create(dto2);

        assertEquals(100, result1.getApplicationId());
        assertEquals(200, result2.getApplicationId());
        verify(authCodeRepository, times(2)).create(any(CreateAuthCodeDto.class));
    }
}
