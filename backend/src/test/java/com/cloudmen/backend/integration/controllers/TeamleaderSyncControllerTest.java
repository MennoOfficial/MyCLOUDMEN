package com.cloudmen.backend.integration.controllers;

import com.cloudmen.backend.api.controllers.TeamleaderSyncController;
import com.cloudmen.backend.services.CompanySyncService;
import com.cloudmen.backend.services.TeamleaderCompanyService;
import com.cloudmen.backend.services.TeamleaderOAuthService;
import com.cloudmen.backend.services.UserSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for TeamleaderSyncController
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeamleaderSyncController Tests")
public class TeamleaderSyncControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TeamleaderOAuthService oAuthService;

    @Mock
    private TeamleaderCompanyService companyService;

    @Mock
    private CompanySyncService companySyncService;

    @Mock
    private UserSyncService userSyncService;

    // Use a real ObjectMapper instead of a mock
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Create a fresh controller for each test
    private TeamleaderSyncController teamleaderSyncController;

    @BeforeEach
    void setUp() {
        // Create a new controller for each test to avoid state issues
        teamleaderSyncController = new TeamleaderSyncController(
                oAuthService, companyService, companySyncService, userSyncService, objectMapper);

        // Create standalone MockMvc to avoid loading full application context
        mockMvc = MockMvcBuilders
                .standaloneSetup(teamleaderSyncController)
                .build();
    }

    @Test
    @DisplayName("POST /api/teamleader/sync/companies - Success")
    void syncCompanies_Success() throws Exception {
        // Arrange
        when(oAuthService.hasValidToken()).thenReturn(true);

        ObjectNode testResult = objectMapper.createObjectNode();
        testResult.put("success", true);
        when(companyService.testApiConnection()).thenReturn(testResult);

        Map<String, Object> syncResult = new HashMap<>();
        syncResult.put("success", true);
        syncResult.put("companiesProcessed", 10);
        syncResult.put("companiesAdded", 2);
        syncResult.put("companiesUpdated", 8);

        CompletableFuture<Map<String, Object>> future = CompletableFuture.completedFuture(syncResult);
        when(companySyncService.syncAllCompanies()).thenReturn(future);

        // Act
        MvcResult result = mockMvc.perform(post("/api/teamleader/sync/companies")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        ObjectNode responseJson = objectMapper.readValue(responseBody, ObjectNode.class);

        assertEquals("success", responseJson.get("status").asText());
        assertTrue(responseJson.get("syncStarted").asBoolean());

        // Verify
        verify(oAuthService).hasValidToken();
        verify(companyService).testApiConnection();
        verify(companySyncService).syncAllCompanies();
    }

    @Test
    @DisplayName("POST /api/teamleader/sync/companies - Not Authorized")
    void syncCompanies_NotAuthorized() throws Exception {
        // Arrange
        when(oAuthService.hasValidToken()).thenReturn(false);

        // Act
        MvcResult result = mockMvc.perform(post("/api/teamleader/sync/companies")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        ObjectNode responseJson = objectMapper.readValue(responseBody, ObjectNode.class);

        assertEquals("error", responseJson.get("status").asText());
        assertEquals(false, responseJson.get("authorized").asBoolean());

        // Verify
        verify(oAuthService).hasValidToken();
        verifyNoInteractions(companyService);
        verifyNoInteractions(companySyncService);
    }

    @Test
    @DisplayName("POST /api/teamleader/sync/companies - API Connection Failure")
    void syncCompanies_ApiConnectionFailure() throws Exception {
        // Arrange
        when(oAuthService.hasValidToken()).thenReturn(true);

        ObjectNode testResult = objectMapper.createObjectNode();
        testResult.put("error", true);
        testResult.put("message", "API connection failed");
        when(companyService.testApiConnection()).thenReturn(testResult);

        // Act
        MvcResult result = mockMvc.perform(post("/api/teamleader/sync/companies")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        ObjectNode responseJson = objectMapper.readValue(responseBody, ObjectNode.class);

        assertEquals("error", responseJson.get("status").asText());

        // Verify
        verify(oAuthService).hasValidToken();
        verify(companyService).testApiConnection();
        verifyNoInteractions(companySyncService);
    }

    @Test
    @DisplayName("GET /api/teamleader/sync/status - No Previous Sync")
    void getSyncStatus_NoPreviousSync() throws Exception {
        // Act
        MvcResult result = mockMvc.perform(get("/api/teamleader/sync/status")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        ObjectNode responseJson = objectMapper.readValue(responseBody, ObjectNode.class);

        assertEquals(false, responseJson.get("hasRun").asBoolean());

        // No need to verify anything as this just returns internal state
    }

    @Test
    @DisplayName("POST /api/teamleader/sync/refresh-custom-fields - Success")
    void refreshCustomFields_Success() throws Exception {
        // Arrange
        Map<String, Object> refreshResult = new HashMap<>();
        refreshResult.put("success", true);
        refreshResult.put("fieldsProcessed", 5);

        CompletableFuture<Map<String, Object>> future = CompletableFuture.completedFuture(refreshResult);
        when(companySyncService.refreshCustomFields()).thenReturn(future);

        // Act
        MvcResult result = mockMvc.perform(post("/api/teamleader/sync/refresh-custom-fields")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        ObjectNode responseJson = objectMapper.readValue(responseBody, ObjectNode.class);

        assertEquals("processing", responseJson.get("status").asText());

        // Verify
        verify(companySyncService).refreshCustomFields();
    }
}