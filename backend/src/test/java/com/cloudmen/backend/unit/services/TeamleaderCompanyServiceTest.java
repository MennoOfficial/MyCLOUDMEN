package com.cloudmen.backend.unit.services;

import com.cloudmen.backend.config.TeamleaderApiConfig;
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
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for TeamleaderCompanyService using a direct approach with standard
 * Mockito.
 * Each test focuses on a single function to make tests easier to understand and
 * maintain.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeamleaderCompanyService Tests")
class TeamleaderCompanyServiceTest {

    @Mock
    private TeamleaderOAuthService oAuthService;

    @Mock
    private TeamleaderApiConfig apiConfig;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private TeamleaderCompanyRepository companyRepository;

    @Mock
    private WebClient webClient;

    @Mock
    private Retry webClientRetrySpec;

    // WebClient chain mocks - for WebClient tests only
    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private WebClient.RequestBodySpec requestBodySpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;

    // Service under test
    private TeamleaderCompanyService teamleaderCompanyService;

    @BeforeEach
    void setUp() {
        teamleaderCompanyService = new TeamleaderCompanyService(
                oAuthService, apiConfig, objectMapper, companyRepository, webClient, webClientRetrySpec);
    }

    @Test
    @DisplayName("getCompanyByTeamleaderId should return company when it exists")
    void getCompanyByTeamleaderId_shouldReturnCompanyWhenItExists() {
        // Arrange
        String teamleaderId = "12345";
        TeamleaderCompany expectedCompany = new TeamleaderCompany();
        expectedCompany.setTeamleaderId(teamleaderId);
        expectedCompany.setName("Test Company");

        when(companyRepository.findByTeamleaderId(teamleaderId)).thenReturn(Optional.of(expectedCompany));

        // Act
        TeamleaderCompany result = teamleaderCompanyService.getCompanyByTeamleaderId(teamleaderId);

        // Assert
        assertNotNull(result);
        assertEquals(teamleaderId, result.getTeamleaderId());
        assertEquals("Test Company", result.getName());
    }

    @Test
    @DisplayName("getAllCompanies should return all companies from repository")
    void getAllCompanies_shouldReturnAllCompaniesFromRepository() {
        // Arrange
        List<TeamleaderCompany> expectedCompanies = Arrays.asList(
                createTestCompany("1", "Company 1"),
                createTestCompany("2", "Company 2"));
        when(companyRepository.findAll()).thenReturn(expectedCompanies);

        // Act
        List<TeamleaderCompany> result = teamleaderCompanyService.getAllCompanies();

        // Assert
        assertEquals(2, result.size());
        assertEquals("Company 1", result.get(0).getName());
        assertEquals("Company 2", result.get(1).getName());
    }

    @Test
    @DisplayName("getCompanies should return error when WebClient is null")
    void getCompanies_shouldReturnErrorWhenWebClientIsNull() {
        // Arrange
        ObjectNode errorNode = mock(ObjectNode.class);
        when(objectMapper.createObjectNode()).thenReturn(errorNode);
        when(errorNode.put(anyString(), anyBoolean())).thenReturn(errorNode);
        when(errorNode.put(anyString(), anyString())).thenReturn(errorNode);

        // Create service with null WebClient
        teamleaderCompanyService = new TeamleaderCompanyService(
                oAuthService, apiConfig, objectMapper, companyRepository, null, webClientRetrySpec);

        // Act
        JsonNode result = teamleaderCompanyService.getCompanies(1, 10);

        // Assert
        verify(objectMapper).createObjectNode();
        assertSame(errorNode, result);
    }

    @Test
    @DisplayName("getCompanies should return error when OAuth token is invalid")
    void getCompanies_shouldReturnErrorWhenOAuthTokenIsInvalid() {
        // Arrange
        ObjectNode errorNode = mock(ObjectNode.class);
        when(objectMapper.createObjectNode()).thenReturn(errorNode);
        when(errorNode.put(anyString(), anyBoolean())).thenReturn(errorNode);
        when(errorNode.put(anyString(), anyString())).thenReturn(errorNode);

        when(oAuthService.hasValidToken()).thenReturn(false);

        // Act
        JsonNode result = teamleaderCompanyService.getCompanies(1, 10);

        // Assert
        verify(oAuthService).hasValidToken();
        assertSame(errorNode, result);
    }

    @Test
    @DisplayName("getCompanies should successfully retrieve companies")
    void getCompanies_shouldSuccessfullyRetrieveCompanies() {
        // Mock response data
        ObjectNode responseNode = mock(ObjectNode.class);

        // Mock ObjectMapper and request objects
        ObjectNode requestNode = mock(ObjectNode.class);
        ObjectNode paginationNode = mock(ObjectNode.class);

        // Align with the actual implementation in TeamleaderCompanyService.getCompanies
        when(objectMapper.createObjectNode())
                .thenReturn(requestNode)
                .thenReturn(paginationNode);
        when(paginationNode.put(eq("size"), anyInt())).thenReturn(paginationNode);
        when(paginationNode.put(eq("number"), anyInt())).thenReturn(paginationNode);
        when(requestNode.set(eq("page"), same(paginationNode))).thenReturn(requestNode);

        // Set up WebClient chain
        doReturn(requestBodyUriSpec).when(webClient).post();
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(eq("/companies.list"));
        doReturn(requestBodySpec).when(requestBodySpec).headers(any(Consumer.class));
        doReturn(requestBodySpec).when(requestBodySpec).contentType(eq(MediaType.APPLICATION_JSON));
        doReturn(requestBodySpec).when(requestBodySpec).bodyValue(same(requestNode));
        doReturn(responseSpec).when(requestBodySpec).retrieve();
        doReturn(Mono.just(responseNode)).when(responseSpec).bodyToMono(JsonNode.class);

        // Mock OAuth token
        when(oAuthService.hasValidToken()).thenReturn(true);
        when(oAuthService.getAccessToken()).thenReturn("mock-token");

        // Act
        JsonNode result = teamleaderCompanyService.getCompanies(1, 10);

        // Assert
        assertNotNull(result);

        // Verify interactions
        verify(webClient).post();
        verify(requestBodyUriSpec).uri("/companies.list");
        verify(requestBodySpec).bodyValue(requestNode);
        verify(responseSpec).bodyToMono(JsonNode.class);

        // Verify the object creation and configuration
        verify(objectMapper, atLeast(2)).createObjectNode();
        verify(paginationNode).put(eq("size"), eq(10));
        verify(paginationNode).put(eq("number"), eq(1));
        verify(requestNode).set(eq("page"), same(paginationNode));
    }

    @Test
    @DisplayName("getCompanyByTeamleaderId should return null when company doesn't exist")
    void getCompanyByTeamleaderId_shouldReturnNullWhenCompanyDoesntExist() {
        // Arrange
        String teamleaderId = "non-existent-id";
        when(companyRepository.findByTeamleaderId(teamleaderId)).thenReturn(Optional.empty());

        // Act
        TeamleaderCompany result = teamleaderCompanyService.getCompanyByTeamleaderId(teamleaderId);

        // Assert
        assertNull(result);
        verify(companyRepository).findByTeamleaderId(teamleaderId);
    }

    @Test
    @DisplayName("getAllCompanies should return empty list when no companies exist")
    void getAllCompanies_shouldReturnEmptyListWhenNoCompaniesExist() {
        // Arrange
        when(companyRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        List<TeamleaderCompany> result = teamleaderCompanyService.getAllCompanies();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(companyRepository).findAll();
    }

    @Test
    @DisplayName("getCompanies should handle API errors and return error response")
    void getCompanies_shouldHandleApiErrorsAndReturnErrorResponse() {
        // Arrange
        ObjectNode errorNode = mock(ObjectNode.class);
        ObjectNode requestNode = mock(ObjectNode.class);
        ObjectNode paginationNode = mock(ObjectNode.class);

        // Set up the proper chain of mocks to match implementation
        when(objectMapper.createObjectNode())
                .thenReturn(requestNode)
                .thenReturn(paginationNode)
                .thenReturn(errorNode);

        // Stub the pagination node methods with proper matchers
        when(paginationNode.put(eq("size"), anyInt())).thenReturn(paginationNode);
        when(paginationNode.put(eq("number"), anyInt())).thenReturn(paginationNode);
        when(requestNode.set(eq("page"), same(paginationNode))).thenReturn(requestNode);

        // Error node stubs - these are what we need for error handling
        when(errorNode.put(eq("error"), anyBoolean())).thenReturn(errorNode);
        when(errorNode.put(eq("message"), anyString())).thenReturn(errorNode);

        // Mock OAuth token
        when(oAuthService.hasValidToken()).thenReturn(true);
        when(oAuthService.getAccessToken()).thenReturn("mock-token");

        // Mock WebClient chain with error
        doReturn(requestBodyUriSpec).when(webClient).post();
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(anyString());
        doReturn(requestBodySpec).when(requestBodySpec).headers(any(Consumer.class));
        doReturn(requestBodySpec).when(requestBodySpec).contentType(any());
        doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        doReturn(responseSpec).when(requestBodySpec).retrieve();

        // Mock error by returning a Mono with error
        RuntimeException apiException = new RuntimeException("API Error");
        doReturn(Mono.error(apiException)).when(responseSpec).bodyToMono(JsonNode.class);

        // Act
        JsonNode result = teamleaderCompanyService.getCompanies(1, 10);

        // Assert
        assertNotNull(result);
        // Verify error handling was triggered
        verify(errorNode).put(eq("error"), eq(true));
        verify(errorNode).put(eq("message"), anyString());
    }

    @Test
    @DisplayName("searchCompaniesByName should return matching companies")
    void searchCompaniesByName_shouldReturnMatchingCompanies() {
        // Arrange
        String searchName = "test";
        List<TeamleaderCompany> expectedCompanies = Arrays.asList(
                createTestCompany("1", "Test Company"),
                createTestCompany("2", "Another Test"));

        when(companyRepository.findByNameContainingIgnoreCase(searchName))
                .thenReturn(expectedCompanies);

        // Act
        Iterable<TeamleaderCompany> result = teamleaderCompanyService.searchCompaniesByName(searchName);

        // Assert
        assertNotNull(result);
        List<TeamleaderCompany> resultList = new ArrayList<>();
        result.forEach(resultList::add);

        assertEquals(2, resultList.size());
        assertEquals("Test Company", resultList.get(0).getName());
        assertEquals("Another Test", resultList.get(1).getName());

        verify(companyRepository).findByNameContainingIgnoreCase(searchName);
    }

    @Test
    @DisplayName("saveCompany should save and return the company")
    void saveCompany_shouldSaveAndReturnCompany() {
        // Arrange
        TeamleaderCompany company = createTestCompany("123", "New Company");
        when(companyRepository.save(any(TeamleaderCompany.class))).thenReturn(company);

        // Act
        TeamleaderCompany result = teamleaderCompanyService.saveCompany(company);

        // Assert
        assertNotNull(result);
        assertEquals("123", result.getTeamleaderId());
        assertEquals("New Company", result.getName());

        verify(companyRepository).save(company);
    }

    // Simple helper
    private TeamleaderCompany createTestCompany(String id, String name) {
        TeamleaderCompany company = new TeamleaderCompany();
        company.setTeamleaderId(id);
        company.setName(name);
        return company;
    }
}