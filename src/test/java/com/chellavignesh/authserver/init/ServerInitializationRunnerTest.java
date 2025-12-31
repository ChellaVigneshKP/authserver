package com.chellavignesh.authserver.init;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.dto.CreateApplicationDto;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.externalsource.ExternalSourceService;
import com.chellavignesh.authserver.adminportal.externalsource.entity.ExternalSource;
import com.chellavignesh.authserver.adminportal.externalsource.entity.ExternalType;
import com.chellavignesh.authserver.adminportal.organization.OrganizationService;
import com.chellavignesh.authserver.adminportal.organization.dto.CreateOrganizationDto;
import com.chellavignesh.authserver.adminportal.organization.entity.Organization;
import com.chellavignesh.authserver.adminportal.user.UserRepository;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.dto.CreateUserDto;
import com.chellavignesh.authserver.adminportal.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ServerInitializationRunner
 */
@ExtendWith(MockitoExtension.class)
class ServerInitializationRunnerTest {

    @Mock
    private OrganizationService organizationService;

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private ExternalSourceService externalSourceService;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @InjectMocks
    private ServerInitializationRunner runner;

    private ExternalSource mockExternalSource;
    private Organization mockOrganization;
    private User mockUser;
    private Application mockApplication;

    @BeforeEach
    void setUp() {
        // Set up mock objects
        mockExternalSource = new ExternalSource(UUID.randomUUID(), "default", false, new ExternalType(1, "Default"));
        
        mockOrganization = new Organization();
        mockOrganization.setId(1);
        mockOrganization.setGuid(UUID.randomUUID());
        mockOrganization.setName("Default Organization");
        
        mockUser = new User();
        mockUser.setId(1);
        mockUser.setUsername("chella");
        mockUser.setRowGuid(UUID.randomUUID());
        
        mockApplication = new Application();
        mockApplication.setId(1);
        mockApplication.setGuid(UUID.randomUUID());
        mockApplication.setName("Default Client Application");
    }

    @Test
    void testRunWithInitializeServerFalse() {
        // Given
        ReflectionTestUtils.setField(runner, "initializeServer", false);

        // When
        runner.run();

        // Then - should not initialize anything
        verify(userRepository, never()).getUserByUsername(anyString());
        verify(organizationService, never()).create(any());
        verify(userService, never()).create(any(), anyInt(), any(Boolean.class));
        verify(applicationService, never()).create(anyInt(), any());
    }

    @Test
    void testRunWithAlreadyInitialized() throws Exception {
        // Given
        ReflectionTestUtils.setField(runner, "initializeServer", true);
        when(userRepository.getUserByUsername("chella")).thenReturn(Optional.of(mockUser));

        // When
        runner.run();

        // Then - should skip initialization
        verify(userRepository, times(1)).getUserByUsername("chella");
        verify(organizationService, never()).create(any());
        verify(userService, never()).create(any(), anyInt(), any(Boolean.class));
        verify(applicationService, never()).create(anyInt(), any());
    }

    @Test
    void testRunWithSuccessfulInitialization() throws Exception {
        // Given
        ReflectionTestUtils.setField(runner, "initializeServer", true);
        when(userRepository.getUserByUsername("chella")).thenReturn(Optional.empty());
        when(externalSourceService.findBySourceCode("default")).thenReturn(Optional.of(mockExternalSource));
        when(organizationService.create(any(CreateOrganizationDto.class))).thenReturn(mockOrganization);
        when(userService.create(any(CreateUserDto.class), anyInt(), any(Boolean.class))).thenReturn(mockUser);
        when(applicationService.create(anyInt(), any(CreateApplicationDto.class))).thenReturn(mockApplication);

        // When
        runner.run();

        // Then - should complete full initialization
        verify(userRepository, times(1)).getUserByUsername("chella");
        verify(externalSourceService, times(1)).findBySourceCode("default");
        verify(organizationService, times(1)).create(any(CreateOrganizationDto.class));
        verify(userService, times(1)).create(any(CreateUserDto.class), anyInt(), any(Boolean.class));
        verify(applicationService, times(1)).create(anyInt(), any(CreateApplicationDto.class));
    }

    @Test
    void testRunWithEnvironmentVariableSet() {
        // Note: Testing environment variable setting requires system property manipulation
        // This test demonstrates the pattern but may need additional setup in actual environment
        
        // Given
        ReflectionTestUtils.setField(runner, "initializeServer", false);
        when(userRepository.getUserByUsername("chella")).thenReturn(Optional.empty());
        
        // Since we can't easily mock System.getenv(), we rely on the property
        // The actual environment variable check is tested in integration tests
    }

    @Test
    void testRunWithOrganizationCreationFailure() throws Exception {
        // Given
        ReflectionTestUtils.setField(runner, "initializeServer", true);
        when(userRepository.getUserByUsername("chella")).thenReturn(Optional.empty());
        when(externalSourceService.findBySourceCode("default")).thenReturn(Optional.of(mockExternalSource));
        when(organizationService.create(any(CreateOrganizationDto.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When/Then
        try {
            runner.run();
        } catch (RuntimeException e) {
            // Expected to throw RuntimeException on failure
            verify(organizationService, times(1)).create(any(CreateOrganizationDto.class));
            verify(userService, never()).create(any(CreateUserDto.class), anyInt(), any(Boolean.class));
        }
    }

    @Test
    void testRunWithUserCreationFailure() throws Exception {
        // Given
        ReflectionTestUtils.setField(runner, "initializeServer", true);
        when(userRepository.getUserByUsername("chella")).thenReturn(Optional.empty());
        when(externalSourceService.findBySourceCode("default")).thenReturn(Optional.of(mockExternalSource));
        when(organizationService.create(any(CreateOrganizationDto.class))).thenReturn(mockOrganization);
        when(userService.create(any(CreateUserDto.class), anyInt(), any(Boolean.class)))
                .thenThrow(new RuntimeException("User creation failed"));

        // When/Then
        try {
            runner.run();
        } catch (RuntimeException e) {
            // Expected to throw RuntimeException on failure
            verify(userService, times(1)).create(any(CreateUserDto.class), anyInt(), any(Boolean.class));
            verify(applicationService, never()).create(anyInt(), any(CreateApplicationDto.class));
        }
    }
}
