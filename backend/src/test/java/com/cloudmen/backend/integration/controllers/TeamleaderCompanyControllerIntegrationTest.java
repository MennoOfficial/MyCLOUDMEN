package com.cloudmen.backend.integration.controllers;

import com.cloudmen.backend.api.controllers.TeamleaderCompanyController;
import com.cloudmen.backend.api.dtos.companies.CompanyDetailDTO;
import com.cloudmen.backend.api.dtos.companies.CompanyListDTO;
import com.cloudmen.backend.domain.enums.CompanyStatusType;
import com.cloudmen.backend.domain.models.TeamleaderCompany;
import com.cloudmen.backend.repositories.TeamleaderCompanyRepository;
import com.cloudmen.backend.services.TeamleaderCompanyService;
import com.cloudmen.backend.services.TeamleaderOAuthService;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for TeamleaderCompanyController
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeamleaderCompanyController Integration Tests")
public class TeamleaderCompanyControllerIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private TeamleaderCompanyService companyService;

    @Mock
    private TeamleaderOAuthService oAuthService;

    @Mock
    private TeamleaderCompanyRepository companyRepository;

    // Use a real ObjectMapper with JavaTimeModule for date handling
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    // Create controller directly
    private TeamleaderCompanyController teamleaderCompanyController;

    @BeforeEach
    void setUp() {
        // Create a new controller for each test
        teamleaderCompanyController = new TeamleaderCompanyController(
                companyService,
                oAuthService,
                objectMapper,
                companyRepository);

        // Create standalone MockMvc
        mockMvc = MockMvcBuilders
                .standaloneSetup(teamleaderCompanyController)
                .build();
    }

    @Test
    @DisplayName("GET /api/teamleader/companies - Returns list of companies")
    void getCompanies_ReturnsListOfCompanies() throws Exception {
        // Arrange
        TeamleaderCompany company1 = new TeamleaderCompany("tl1", "Company 1");
        company1.setId("id1");
        company1.setSyncedAt(LocalDateTime.now());
        company1.setStatus(CompanyStatusType.ACTIVE);

        TeamleaderCompany company2 = new TeamleaderCompany("tl2", "Company 2");
        company2.setId("id2");
        company2.setSyncedAt(LocalDateTime.now());
        company2.setStatus(CompanyStatusType.ACTIVE);

        List<TeamleaderCompany> companies = Arrays.asList(company1, company2);

        when(companyService.getAllCompanies()).thenReturn(companies);

        // Act
        MvcResult result = mockMvc.perform(get("/api/teamleader/companies")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

        assertTrue(responseMap.containsKey("companies"));
        List<?> returnedCompanies = (List<?>) responseMap.get("companies");
        assertEquals(2, returnedCompanies.size());

        // Verify
        verify(companyService).getAllCompanies();
    }

    @Test
    @DisplayName("GET /api/teamleader/companies/{id} - Returns company details when found")
    void getCompanyDetails_ReturnsDetails_WhenFound() throws Exception {
        // Arrange
        String companyId = "tl123";
        TeamleaderCompany company = new TeamleaderCompany(companyId, "Test Company");
        company.setId("id123");
        company.setVatNumber("BE0123456789");
        company.setStatus(CompanyStatusType.ACTIVE);

        when(companyService.getCompanyByTeamleaderId(companyId)).thenReturn(company);

        // Act
        MvcResult result = mockMvc.perform(get("/api/teamleader/companies/{id}", companyId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

        assertEquals(companyId, responseMap.get("teamleaderId"));
        assertEquals("Test Company", responseMap.get("name"));
        assertEquals("BE0123456789", responseMap.get("vatNumber"));

        // Verify
        verify(companyService).getCompanyByTeamleaderId(companyId);
    }

    @Test
    @DisplayName("GET /api/teamleader/companies/{id} - Returns error when not found")
    void getCompanyDetails_ReturnsError_WhenNotFound() throws Exception {
        // Arrange
        String companyId = "nonexistent";
        when(companyService.getCompanyByTeamleaderId(companyId)).thenReturn(null);

        // Act
        MvcResult result = mockMvc.perform(get("/api/teamleader/companies/{id}", companyId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

        assertTrue((Boolean) responseMap.get("error"));
        assertTrue(((String) responseMap.get("message")).contains("not found"));

        // Verify
        verify(companyService).getCompanyByTeamleaderId(companyId);
    }

    @Test
    @DisplayName("GET /api/teamleader/companies/search - Returns matching companies")
    void searchCompanies_ReturnsMatchingCompanies() throws Exception {
        // Arrange
        String searchQuery = "test";
        TeamleaderCompany company = new TeamleaderCompany("tl1", "Test Company");
        company.setId("id1");

        List<TeamleaderCompany> matchingCompanies = Arrays.asList(company);
        when(companyService.searchCompaniesByName(searchQuery)).thenReturn(matchingCompanies);

        // Act
        MvcResult result = mockMvc.perform(get("/api/teamleader/companies/search")
                .param("query", searchQuery)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        List<?> responseList = objectMapper.readValue(responseBody, List.class);

        assertEquals(1, responseList.size());

        // Verify
        verify(companyService).searchCompaniesByName(searchQuery);
    }

    @Test
    @DisplayName("GET /api/teamleader/companies/remote/{id} - Requires valid token")
    void getCompanyDetailsFromApi_RequiresValidToken() throws Exception {
        // Arrange
        String companyId = "tl123";
        when(oAuthService.hasValidToken()).thenReturn(false);

        // Act
        MvcResult result = mockMvc.perform(get("/api/teamleader/companies/remote/{id}", companyId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();

        // Debug the response
        System.out.println("Response body: " + responseBody);

        // Instead of relying on specific response structure, check the content contains
        // expected messages
        assertTrue(responseBody.contains("error") || responseBody.contains("not authorized") ||
                responseBody.contains("unauthorized") || responseBody.contains("permission"),
                "Response should indicate authorization error");

        // Verify
        verify(oAuthService).hasValidToken();
        verifyNoInteractions(companyService);
    }

    @Test
    @DisplayName("GET /api/teamleader/companies/remote/{id} - Returns company details when token valid")
    void getCompanyDetailsFromApi_ReturnsDetails_WhenTokenValid() throws Exception {
        // Arrange
        String companyId = "tl123";
        when(oAuthService.hasValidToken()).thenReturn(true);

        ObjectNode companyDetails = objectMapper.createObjectNode();
        companyDetails.put("id", companyId);
        companyDetails.put("name", "Remote Company");

        when(companyService.getCompanyDetails(companyId)).thenReturn(companyDetails);

        // Act
        MvcResult result = mockMvc.perform(get("/api/teamleader/companies/remote/{id}", companyId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        JsonNode responseJson = objectMapper.readTree(responseBody);

        assertEquals(companyId, responseJson.get("id").asText());
        assertEquals("Remote Company", responseJson.get("name").asText());

        // Verify
        verify(oAuthService).hasValidToken();
        verify(companyService).getCompanyDetails(companyId);
    }
}