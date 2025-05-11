package com.cloudmen.backend.integration.security;

import com.cloudmen.backend.api.controllers.Auth0Controller;
import com.cloudmen.backend.domain.models.AuthenticationLog;
import com.cloudmen.backend.domain.models.User;
import com.cloudmen.backend.services.AuthenticationLogService;
import com.cloudmen.backend.services.UserSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Auth0Controller using MockMvc standalone setup
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Auth0Controller Tests")
public class Auth0ControllerIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private AuthenticationLogService authenticationLogService;

    @Mock
    private UserSyncService userSyncService;

    // Use a real ObjectMapper instead of injecting it
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Create controller directly instead of using @InjectMocks
    private Auth0Controller auth0Controller;

    @BeforeEach
    void setUp() {
        // Create a new controller for each test to avoid state issues
        auth0Controller = new Auth0Controller(authenticationLogService, userSyncService);

        // Create standalone MockMvc to avoid loading full application context
        mockMvc = MockMvcBuilders
                .standaloneSetup(auth0Controller)
                .build();
    }

    @Test
    @DisplayName("POST /api/auth0/log-authentication - Success")
    void logAuthentication_Success() throws Exception {
        // Arrange
        Map<String, Object> authData = new HashMap<>();
        authData.put("email", "test@example.com");
        authData.put("sub", "auth0|123456789");
        authData.put("name", "Test User");

        // Mock behavior
        AuthenticationLog mockLog = new AuthenticationLog();
        mockLog.setId("test-log-id");
        mockLog.setEmail("test@example.com");
        mockLog.setSuccessful(true);
        mockLog.setTimestamp(LocalDateTime.now());

        User mockUser = new User();
        mockUser.setEmail("test@example.com");
        mockUser.setId("test-user-id");

        when(userSyncService.syncUserWithAuth0(anyString(), any()))
                .thenReturn(mockUser);
        when(authenticationLogService.logSuccessfulAuthentication(anyString(), anyString(), anyString()))
                .thenReturn(mockLog);

        // Act
        MvcResult result = mockMvc.perform(post("/api/auth0/log-authentication")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authData)))
                .andExpect(status().isOk())
                .andReturn();

        // Verify interactions
        verify(userSyncService).syncUserWithAuth0(eq("test@example.com"), any());
        verify(authenticationLogService).logSuccessfulAuthentication(
                eq("test@example.com"),
                anyString(),
                anyString());
    }

    @Test
    @DisplayName("POST /api/auth0/log-authentication - Bad Request when email is missing")
    void logAuthentication_BadRequest_WhenEmailMissing() throws Exception {
        // Arrange
        Map<String, Object> authData = new HashMap<>();
        authData.put("sub", "auth0|123456789");
        authData.put("name", "Test User");

        // Act
        MvcResult result = mockMvc.perform(post("/api/auth0/log-authentication")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authData)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Verify no interactions with services
        verifyNoInteractions(userSyncService);
        verifyNoInteractions(authenticationLogService);
    }

    @Test
    @DisplayName("POST /api/auth0/log-authentication-failure - Success")
    void logAuthenticationFailure_Success() throws Exception {
        // Arrange
        Map<String, String> authData = new HashMap<>();
        authData.put("email", "test@example.com");
        authData.put("reason", "Invalid credentials");

        // Mock behavior
        AuthenticationLog mockLog = new AuthenticationLog();
        mockLog.setId("test-log-id");
        mockLog.setEmail("test@example.com");
        mockLog.setSuccessful(false);
        mockLog.setFailureReason("Invalid credentials");
        mockLog.setTimestamp(LocalDateTime.now());

        when(authenticationLogService.logFailedAuthentication(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(mockLog);

        // Act
        MvcResult result = mockMvc.perform(post("/api/auth0/log-authentication-failure")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authData)))
                .andExpect(status().isOk())
                .andReturn();

        // Verify interactions
        verify(authenticationLogService).logFailedAuthentication(
                eq("test@example.com"),
                anyString(),
                anyString(),
                eq("Invalid credentials"));
    }

    @Test
    @DisplayName("POST /api/auth0/log-authentication-failure - Success with null email")
    void logAuthenticationFailure_Success_WithNullEmail() throws Exception {
        // Arrange
        Map<String, String> authData = new HashMap<>();
        authData.put("reason", "Unknown user");

        // Mock behavior
        AuthenticationLog mockLog = new AuthenticationLog();
        mockLog.setId("test-log-id");
        mockLog.setSuccessful(false);
        mockLog.setFailureReason("Unknown user");
        mockLog.setTimestamp(LocalDateTime.now());

        when(authenticationLogService.logFailedAuthentication(
                isNull(), anyString(), anyString(), anyString()))
                .thenReturn(mockLog);

        // Act
        MvcResult result = mockMvc.perform(post("/api/auth0/log-authentication-failure")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authData)))
                .andExpect(status().isOk())
                .andReturn();

        // Verify interactions
        verify(authenticationLogService).logFailedAuthentication(
                isNull(),
                anyString(),
                anyString(),
                eq("Unknown user"));
    }
}