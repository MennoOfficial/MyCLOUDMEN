package com.cloudmen.backend.integration.controllers;

import com.cloudmen.backend.api.controllers.TeamleaderOAuthController;
import com.cloudmen.backend.domain.models.OAuthToken;
import com.cloudmen.backend.services.TeamleaderOAuthService;
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
import org.springframework.web.servlet.view.RedirectView;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for TeamleaderOAuthController
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeamleaderOAuthController Integration Tests")
public class TeamleaderOAuthControllerIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private TeamleaderOAuthService oAuthService;

    // Use a real ObjectMapper
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Create controller directly
    private TeamleaderOAuthController oAuthController;

    @BeforeEach
    void setUp() {
        // Create a new controller for each test
        oAuthController = new TeamleaderOAuthController(oAuthService);

        // Create standalone MockMvc
        mockMvc = MockMvcBuilders
                .standaloneSetup(oAuthController)
                .build();
    }

    @Test
    @DisplayName("GET /api/teamleader/oauth/authorize - Redirects to authorization URL")
    void authorize_RedirectsToAuthUrl() throws Exception {
        // Arrange
        String authUrl = "https://teamleader.com/oauth/authorize?client_id=test";
        when(oAuthService.getAuthorizationUrl()).thenReturn(authUrl);

        // We can't directly test RedirectView with MockMvc, so we'll verify the
        // controller logic instead
        RedirectView result = oAuthController.authorize();

        // Assert
        assertEquals(authUrl, result.getUrl());

        // Verify
        verify(oAuthService).getAuthorizationUrl();
    }

    @Test
    @DisplayName("GET /api/teamleader/oauth/request-access - Redirects to authorization endpoint")
    void requestAccess_RedirectsToAuthEndpoint() throws Exception {
        // Arrange
        String authUrl = "https://teamleader.com/oauth/authorize?client_id=test";
        when(oAuthService.getAuthorizationUrl()).thenReturn(authUrl);

        // We can't directly test RedirectView with MockMvc, so we'll verify the
        // controller logic instead
        RedirectView result = oAuthController.requestAccess();

        // Assert
        assertEquals(authUrl, result.getUrl());

        // Verify
        verify(oAuthService).getAuthorizationUrl();
    }

    @Test
    @DisplayName("GET /api/teamleader/oauth/callback - Returns success when token exchange succeeds")
    void callback_Success_WhenTokenExchangeSucceeds() throws Exception {
        // Arrange
        String code = "test-auth-code";
        String state = "test-state";
        when(oAuthService.exchangeAuthorizationCode(code)).thenReturn(true);

        // Act
        MvcResult result = mockMvc.perform(get("/api/teamleader/oauth/callback")
                .param("code", code)
                .param("state", state)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        Map<String, String> responseMap = objectMapper.readValue(responseBody, Map.class);

        assertEquals("success", responseMap.get("status"));
        assertTrue(responseMap.get("message").contains("Authorization successful"));

        // Verify
        verify(oAuthService).exchangeAuthorizationCode(code);
    }

    @Test
    @DisplayName("GET /api/teamleader/oauth/callback - Returns error when token exchange fails")
    void callback_Error_WhenTokenExchangeFails() throws Exception {
        // Arrange
        String code = "invalid-code";
        when(oAuthService.exchangeAuthorizationCode(code)).thenReturn(false);

        // Act
        MvcResult result = mockMvc.perform(get("/api/teamleader/oauth/callback")
                .param("code", code)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        Map<String, String> responseMap = objectMapper.readValue(responseBody, Map.class);

        assertEquals("error", responseMap.get("status"));
        assertTrue(responseMap.get("message").contains("Failed to exchange"));

        // Verify
        verify(oAuthService).exchangeAuthorizationCode(code);
    }

    @Test
    @DisplayName("GET /api/teamleader/oauth/status - Returns authorized when token exists")
    void status_ReturnsAuthorized_WhenTokenExists() throws Exception {
        // Arrange
        OAuthToken token = new OAuthToken();
        token.setProvider("teamleader");
        token.setLastUpdated(LocalDateTime.now().minusDays(1));
        token.setLastUsed(LocalDateTime.now().minusHours(1));
        token.setAccessTokenExpiresAt(LocalDateTime.now().plusHours(1));

        when(oAuthService.getTokenInfo()).thenReturn(token);

        // Act
        MvcResult result = mockMvc.perform(get("/api/teamleader/oauth/status")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

        assertTrue((Boolean) responseMap.get("authorized"));
        assertEquals("teamleader", responseMap.get("provider"));
        assertFalse((Boolean) responseMap.get("tokenExpired"));

        // Verify
        verify(oAuthService).getTokenInfo();
    }

    @Test
    @DisplayName("GET /api/teamleader/oauth/status - Returns unauthorized when no token exists")
    void status_ReturnsUnauthorized_WhenNoTokenExists() throws Exception {
        // Arrange
        when(oAuthService.getTokenInfo()).thenReturn(null);

        // Act
        MvcResult result = mockMvc.perform(get("/api/teamleader/oauth/status")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

        assertFalse((Boolean) responseMap.get("authorized"));
        assertTrue(responseMap.get("message").toString().contains("not authorized"));

        // Verify
        verify(oAuthService).getTokenInfo();
    }

    @Test
    @DisplayName("POST /api/teamleader/oauth/revoke - Returns success when token revocation succeeds")
    void revoke_ReturnsSuccess_WhenRevocationSucceeds() throws Exception {
        // Arrange
        when(oAuthService.revokeToken()).thenReturn(true);

        // Act
        MvcResult result = mockMvc.perform(post("/api/teamleader/oauth/revoke")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        Map<String, String> responseMap = objectMapper.readValue(responseBody, Map.class);

        assertEquals("success", responseMap.get("status"));
        assertTrue(responseMap.get("message").contains("successfully revoked"));

        // Verify
        verify(oAuthService).revokeToken();
    }

    @Test
    @DisplayName("POST /api/teamleader/oauth/revoke - Returns error when token revocation fails")
    void revoke_ReturnsError_WhenRevocationFails() throws Exception {
        // Arrange
        when(oAuthService.revokeToken()).thenReturn(false);

        // Act
        MvcResult result = mockMvc.perform(post("/api/teamleader/oauth/revoke")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        Map<String, String> responseMap = objectMapper.readValue(responseBody, Map.class);

        assertEquals("error", responseMap.get("status"));
        assertTrue(responseMap.get("message").contains("No token found"));

        // Verify
        verify(oAuthService).revokeToken();
    }
}