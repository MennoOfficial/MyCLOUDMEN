package com.cloudmen.backend.services;

import com.cloudmen.backend.config.TeamleaderApiConfig;
import com.cloudmen.backend.config.TeamleaderConfig;
import com.cloudmen.backend.domain.models.TeamleaderCompany;
import com.cloudmen.backend.repositories.TeamleaderCompanyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service for interacting with Teamleader API to fetch company data
 */
@Service
public class TeamleaderCompanyService {

    private static final Logger logger = LoggerFactory.getLogger(TeamleaderCompanyService.class);
    private final RestTemplate restTemplate;
    private final TeamleaderOAuthService oAuthService;
    private final TeamleaderApiConfig apiConfig;
    private final ObjectMapper objectMapper;
    private final TeamleaderCompanyRepository companyRepository;
    private final TeamleaderConfig teamleaderConfig;

    public TeamleaderCompanyService(
            RestTemplate restTemplate,
            TeamleaderOAuthService oAuthService,
            TeamleaderApiConfig apiConfig,
            ObjectMapper objectMapper,
            TeamleaderCompanyRepository companyRepository,
            TeamleaderConfig teamleaderConfig) {
        this.restTemplate = restTemplate;
        this.oAuthService = oAuthService;
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.companyRepository = companyRepository;
        this.teamleaderConfig = teamleaderConfig;
    }

    /**
     * Get a list of companies from Teamleader
     * 
     * @param page     Page number (1-based)
     * @param pageSize Number of items per page
     * @return List of companies
     */
    public JsonNode getCompanies(int page, int pageSize) {
        if (!oAuthService.hasValidToken()) {
            logger.error("No valid access token available for Teamleader API");
            return createErrorResponse("No valid access token available");
        }

        try {
            String accessToken = oAuthService.getAccessToken();
            HttpHeaders headers = createHeaders(accessToken);

            // Create request body with pagination parameters in the format expected by
            // Teamleader
            ObjectNode requestBody = objectMapper.createObjectNode();

            // According to Teamleader API docs, pagination should be formatted as:
            // { "page": { "size": 20, "number": 1 } }
            ObjectNode paginationNode = objectMapper.createObjectNode();
            paginationNode.put("size", pageSize);
            paginationNode.put("number", page);
            requestBody.set("page", paginationNode);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            String url = apiConfig.getBaseUrl() + "/companies.list";
            logger.info("Sending request to: {}", url);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    JsonNode.class);

            logger.info("Successfully retrieved companies from Teamleader API");
            return response.getBody();
        } catch (HttpClientErrorException e) {
            logger.error("Error fetching companies from Teamleader API: {}", e.getMessage());
            return handleApiError(e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching companies from Teamleader API", e);
            return createErrorResponse("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Get details for a specific company
     * 
     * @param companyId The Teamleader company ID
     * @return Company details
     */
    public JsonNode getCompanyDetails(String companyId) {
        if (!oAuthService.hasValidToken()) {
            logger.error("No valid access token available for Teamleader API");
            return createErrorResponse("No valid access token available");
        }

        try {
            String accessToken = oAuthService.getAccessToken();
            HttpHeaders headers = createHeaders(accessToken);

            // Create request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("id", companyId);

            logger.debug("Request body for companies.info: {}", requestBody);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            String url = apiConfig.getBaseUrl() + "/companies.info";
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    JsonNode.class);

            logger.info("Successfully retrieved company details for ID: {}", companyId);
            logger.debug("Response body: {}", response.getBody());
            return response.getBody();
        } catch (HttpClientErrorException e) {
            logger.error("Error fetching company details from Teamleader API: {}", e.getMessage());
            logger.debug("Error response body: {}", e.getResponseBodyAsString());
            return handleApiError(e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching company details from Teamleader API", e);
            return createErrorResponse("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Get a list of all companies from the database
     * 
     * @return List of companies
     */
    public List<TeamleaderCompany> getAllCompanies() {
        return companyRepository.findAll();
    }

    /**
     * Get a company by its Teamleader ID
     * 
     * @param teamleaderId The Teamleader company ID
     * @return Company details
     */
    public TeamleaderCompany getCompanyByTeamleaderId(String teamleaderId) {
        return companyRepository.findByTeamleaderId(teamleaderId).orElse(null);
    }

    /**
     * Search for companies by name
     * 
     * @param name The name to search for
     * @return List of matching companies
     */
    public Iterable<TeamleaderCompany> searchCompaniesByName(String name) {
        return companyRepository.findByNameContainingIgnoreCase(name);
    }

    /**
     * Save a company to the database
     * 
     * @param company The company to save
     * @return The saved company
     */
    public TeamleaderCompany saveCompany(TeamleaderCompany company) {
        return companyRepository.save(company);
    }

    /**
     * Create headers for Teamleader API requests
     * 
     * @param accessToken Access token
     * @return HTTP headers
     */
    private HttpHeaders createHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return headers;
    }

    /**
     * Handle API errors from Teamleader
     * 
     * @param e The exception
     * @return Error response as JsonNode
     */
    private JsonNode handleApiError(HttpClientErrorException e) {
        ObjectNode errorNode = objectMapper.createObjectNode();
        errorNode.put("error", true);
        errorNode.put("status", e.getStatusCode().value());
        errorNode.put("message", e.getMessage());

        try {
            // Try to parse the response body
            JsonNode responseBody = objectMapper.readTree(e.getResponseBodyAsString());
            errorNode.set("details", responseBody);
        } catch (Exception ex) {
            // If parsing fails, just include the raw response
            errorNode.put("responseBody", e.getResponseBodyAsString());
        }

        return errorNode;
    }

    /**
     * Create a generic error response
     * 
     * @param message Error message
     * @return Error response as JsonNode
     */
    private JsonNode createErrorResponse(String message) {
        ObjectNode errorNode = objectMapper.createObjectNode();
        errorNode.put("error", true);
        errorNode.put("message", message);
        return errorNode;
    }

    /**
     * Test the connection to the Teamleader API
     * 
     * @return A response indicating the connection status
     */
    public JsonNode testApiConnection() {
        if (!oAuthService.hasValidToken()) {
            logger.error("No valid access token available for Teamleader API");
            return createErrorResponse("No valid access token available");
        }

        try {
            String accessToken = oAuthService.getAccessToken();
            HttpHeaders headers = createHeaders(accessToken);

            // Create a simple request to get the current user
            ObjectNode requestBody = objectMapper.createObjectNode();

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            String url = apiConfig.getBaseUrl() + "/users.me";
            logger.info("Testing API connection to: {}", url);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    JsonNode.class);

            logger.info("API connection test successful. Status: {}", response.getStatusCode());

            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "success");
            result.put("message", "Successfully connected to Teamleader API");
            result.set("data", response.getBody());
            return result;
        } catch (HttpClientErrorException e) {
            logger.error("API connection test failed: {}", e.getMessage());
            logger.debug("Error response body: {}", e.getResponseBodyAsString());
            return handleApiError(e);
        } catch (Exception e) {
            logger.error("Unexpected error testing API connection", e);
            return createErrorResponse("Unexpected error: " + e.getMessage());
        }
    }
}