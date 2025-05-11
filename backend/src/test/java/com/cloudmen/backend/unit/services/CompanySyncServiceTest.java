package com.cloudmen.backend.unit.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.cloudmen.backend.domain.models.TeamleaderCompany;
import com.cloudmen.backend.repositories.TeamleaderCompanyRepository;
import com.cloudmen.backend.services.CompanySyncService;
import com.cloudmen.backend.services.TeamleaderCompanyService;
import com.cloudmen.backend.services.UserSyncService;
import com.cloudmen.backend.config.TeamleaderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Unit tests for CompanySyncService
 */
class CompanySyncServiceTest {

    @Mock
    private TeamleaderCompanyRepository companyRepository;

    @Mock
    private TeamleaderCompanyService companyService;

    @Mock
    private UserSyncService userSyncService;

    @Mock
    private TeamleaderConfig teamleaderConfig;

    private ObjectMapper objectMapper;
    private CompanySyncService companySyncService;
    private ObjectNode companiesResponse;
    private ObjectNode companyDetailsResponse;

    @BeforeEach
    void setUp() {
        // Initialize mocks without MockitoExtension to avoid strict stubbing issues
        MockitoAnnotations.openMocks(this);

        // Create real ObjectMapper
        objectMapper = new ObjectMapper();

        // Create the service
        companySyncService = new CompanySyncService(
                companyService,
                companyRepository,
                userSyncService,
                teamleaderConfig);

        // Set up common configuration
        when(teamleaderConfig.getMyCloudmenAccessFieldId()).thenReturn("has_my_cloudmen_access");

        // Create test data
        setupTestData();
    }

    private void setupTestData() {
        // Create company response data
        companiesResponse = objectMapper.createObjectNode();
        ArrayNode dataArray = objectMapper.createArrayNode();

        ObjectNode companyData = objectMapper.createObjectNode();
        companyData.put("id", "tl-123");
        companyData.put("name", "Test Company");
        dataArray.add(companyData);

        companiesResponse.set("data", dataArray);

        // Create company details response
        companyDetailsResponse = objectMapper.createObjectNode();
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", "tl-123");
        data.put("name", "Test Company");
        data.put("website", "https://example.com");

        ObjectNode customFields = objectMapper.createObjectNode();
        customFields.put("has_my_cloudmen_access", true);
        data.set("custom_fields", customFields);

        ArrayNode emails = objectMapper.createArrayNode();
        ObjectNode email = objectMapper.createObjectNode();
        email.put("type", "primary");
        email.put("value", "info@example.com");
        emails.add(email);
        data.set("emails", emails);

        companyDetailsResponse.set("data", data);
    }

    @Test
    @DisplayName("syncAllCompanies should sync all companies successfully")
    void syncAllCompanies_shouldSyncAllCompanies() {
        // Arrange
        when(companyService.getCompanies(anyInt(), anyInt())).thenReturn(companiesResponse);
        when(companyService.getCompanyDetails("tl-123")).thenReturn(companyDetailsResponse);
        when(companyRepository.findByTeamleaderId("tl-123")).thenReturn(Optional.empty());

        // Act
        CompletableFuture<Map<String, Object>> result = companySyncService.syncAllCompanies();

        // Assert
        Map<String, Object> summary = result.join();
        assertTrue((Boolean) summary.get("success"));
        assertEquals(1, summary.get("totalCompanies"));
        verify(userSyncService).updateExistingUserRoles();
    }

    @Test
    @DisplayName("syncAllCompanies should handle API errors")
    void syncAllCompanies_shouldHandleErrors() {
        // Arrange - simulate an API error
        when(companyService.getCompanies(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("API Error"));

        // Act
        CompletableFuture<Map<String, Object>> result = companySyncService.syncAllCompanies();

        // Assert - verify error is handled properly
        Map<String, Object> summary = result.join();
        assertFalse((Boolean) summary.get("success"));
        assertEquals("API Error", summary.get("error"));

        // Verify UserSyncService is not called when there's an error
        verifyNoInteractions(userSyncService);
    }

    @Test
    @DisplayName("syncAllCompanies should handle null API response")
    void syncAllCompanies_shouldHandleNullResponse() {
        // Arrange - simulate null API response
        when(companyService.getCompanies(anyInt(), anyInt())).thenReturn(null);

        // Act
        CompletableFuture<Map<String, Object>> result = companySyncService.syncAllCompanies();

        // Assert - verify error stats are incremented
        Map<String, Object> summary = result.join();

        // When null response, success should still be true but with error count > 0
        assertTrue((Boolean) summary.get("success"));
        assertEquals(0, summary.get("totalCompanies"));
        assertEquals(0, summary.get("created"));
        assertEquals(0, summary.get("updated"));
        assertEquals(1, summary.get("errors"));

        // UserSyncService should still be called since the method doesn't throw an
        // exception
        verify(userSyncService).updateExistingUserRoles();
    }

    @Test
    @DisplayName("refreshCustomFields should update company fields")
    void refreshCustomFields_shouldUpdateCustomFields() {
        // Arrange
        TeamleaderCompany company = new TeamleaderCompany();
        company.setTeamleaderId("tl-123");
        company.setName("Test Company");

        when(companyRepository.findAll()).thenReturn(Collections.singletonList(company));
        when(companyService.getCompanyDetails("tl-123")).thenReturn(companyDetailsResponse);

        // Act
        CompletableFuture<Map<String, Object>> result = companySyncService.refreshCustomFields();

        // Assert
        Map<String, Object> summary = result.join();
        assertTrue((Boolean) summary.get("success"));
        verify(companyRepository).save(any(TeamleaderCompany.class));
    }
}