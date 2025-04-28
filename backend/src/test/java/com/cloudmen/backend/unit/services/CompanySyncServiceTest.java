package com.cloudmen.backend.unit.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cloudmen.backend.domain.models.TeamleaderCompany;
import com.cloudmen.backend.repositories.TeamleaderCompanyRepository;
import com.cloudmen.backend.services.CompanySyncService;
import com.cloudmen.backend.services.TeamleaderCompanyService;
import com.cloudmen.backend.services.UserSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class CompanySyncServiceTest {

    @Mock
    private TeamleaderCompanyRepository companyRepository;

    @Mock
    private TeamleaderCompanyService companyService;

    @Mock
    private UserSyncService userSyncService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CompanySyncService companySyncService;

    private TeamleaderCompany testCompany;
    private ObjectNode testCompanyData;
    private ObjectNode companiesResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Setup test company
        testCompany = new TeamleaderCompany();
        testCompany.setId("1");
        testCompany.setName("Test Company");
        testCompany.setTeamleaderId("tl-123");

        // Create test company data JSON
        testCompanyData = objectMapper.createObjectNode();
        testCompanyData.put("id", "tl-123");
        testCompanyData.put("name", "Test Company");

        // Create companies response with array of data
        companiesResponse = objectMapper.createObjectNode();
        ArrayNode dataArray = objectMapper.createArrayNode();
        dataArray.add(testCompanyData);
        companiesResponse.set("data", dataArray);
    }

    @Test
    @DisplayName("syncAllCompanies should sync all companies")
    void syncAllCompanies_shouldSyncAllCompanies() {
        // Arrange
        when(companyService.getCompanies(anyInt(), anyInt())).thenReturn(companiesResponse);

        ObjectNode companyDetailsResponse = objectMapper.createObjectNode();
        ObjectNode detailsData = objectMapper.createObjectNode();
        detailsData.put("id", "tl-123");
        detailsData.put("name", "Test Company");
        companyDetailsResponse.set("data", detailsData);

        when(companyService.getCompanyDetails("tl-123")).thenReturn(companyDetailsResponse);

        // Act
        CompletableFuture<Map<String, Object>> result = companySyncService.syncAllCompanies();

        // Assert
        assertNotNull(result);
        Map<String, Object> summary = result.join();
        assertTrue((Boolean) summary.get("success"));

        verify(companyService).getCompanies(anyInt(), anyInt());
        verify(companyService).getCompanyDetails("tl-123");
        verify(userSyncService).updateExistingUserRoles();
    }

    @Test
    @DisplayName("refreshCustomFields should update custom fields for all companies")
    void refreshCustomFields_shouldUpdateCustomFields() {
        // Arrange
        when(companyRepository.findAll()).thenReturn(Collections.singletonList(testCompany));

        ObjectNode companyDetails = objectMapper.createObjectNode();
        ObjectNode data = objectMapper.createObjectNode();
        ObjectNode customFields = objectMapper.createObjectNode();
        customFields.put("has_my_cloudmen_access", true);
        data.set("custom_fields", customFields);
        companyDetails.set("data", data);

        when(companyService.getCompanyDetails(testCompany.getTeamleaderId())).thenReturn(companyDetails);

        // Act
        CompletableFuture<Map<String, Object>> result = companySyncService.refreshCustomFields();

        // Assert
        assertNotNull(result);
        Map<String, Object> summary = result.join();
        assertTrue((Boolean) summary.get("success"));

        verify(companyRepository).findAll();
        verify(companyService).getCompanyDetails(testCompany.getTeamleaderId());
        verify(companyRepository).save(any(TeamleaderCompany.class));
    }

    @Test
    @DisplayName("syncAllCompanies should handle errors")
    void syncAllCompanies_shouldHandleErrors() {
        // Arrange
        when(companyService.getCompanies(anyInt(), anyInt())).thenThrow(new RuntimeException("API Error"));

        // Act
        CompletableFuture<Map<String, Object>> result = companySyncService.syncAllCompanies();

        // Assert
        assertNotNull(result);
        Map<String, Object> summary = result.join();
        assertFalse((Boolean) summary.get("success"));
        assertEquals("API Error", summary.get("error"));

        verify(companyService).getCompanies(anyInt(), anyInt());
        verifyNoInteractions(userSyncService);
    }
}