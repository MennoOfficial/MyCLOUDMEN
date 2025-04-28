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
import org.mockito.Mockito;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// For testing purposes
class ApiException extends RuntimeException {
    public ApiException(String message) {
        super(message);
    }
}

/**
 * Tests for TeamleaderCompanyService
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

    // WebClient chain mocks
    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private TeamleaderCompanyService teamleaderCompanyService;

    @BeforeEach
    void setUp() {
        // Set up the service with all required dependencies
        teamleaderCompanyService = new TeamleaderCompanyService(
                oAuthService,
                apiConfig,
                objectMapper,
                companyRepository,
                webClient,
                webClientRetrySpec);

        // We're using lenient() to avoid "unnecessary stubbing" errors
        // when not all tests use these mocks
        lenient().when(apiConfig.getBaseUrl()).thenReturn("https://api.teamleader.eu");

        // Set up default WebClient chain with lenient stubs
        lenient().when(webClient.post()).thenReturn(requestBodyUriSpec);
        lenient().when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);

        // Fix the headers setup - using the proper way to set bearer auth
        lenient().when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);

        lenient().when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        // Provide default responses for Mono types
        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.empty());
        lenient().when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.empty());
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
        verify(companyRepository).findByTeamleaderId(teamleaderId);
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
        verify(companyRepository).findAll();
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