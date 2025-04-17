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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service for interacting with Teamleader API to fetch company data
 */
@Service
public class TeamleaderCompanyService {

    private static final Logger logger = LoggerFactory.getLogger(TeamleaderCompanyService.class);
    private final TeamleaderOAuthService oAuthService;
    private final TeamleaderApiConfig apiConfig;
    private final ObjectMapper objectMapper;
    private final TeamleaderCompanyRepository companyRepository;
    private final TeamleaderConfig teamleaderConfig;

    // WebClient for API requests
    private final WebClient webClient;
    private final Retry webClientRetrySpec;

    public TeamleaderCompanyService(
            TeamleaderOAuthService oAuthService,
            TeamleaderApiConfig apiConfig,
            ObjectMapper objectMapper,
            TeamleaderCompanyRepository companyRepository,
            TeamleaderConfig teamleaderConfig,
            @Autowired(required = false) WebClient webClient,
            @Autowired(required = false) @Qualifier("webClientRetrySpec") Retry webClientRetrySpec) {
        this.oAuthService = oAuthService;
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.companyRepository = companyRepository;
        this.teamleaderConfig = teamleaderConfig;
        this.webClient = webClient;
        this.webClientRetrySpec = webClientRetrySpec;
    }

    /**
     * Get a list of companies from Teamleader using WebClient
     * 
     * @param page     Page number (1-based)
     * @param pageSize Number of items per page
     * @return List of companies
     */
    public JsonNode getCompanies(int page, int pageSize) {
        if (webClient == null) {
            logger.error("WebClient not available");
            return createErrorResponse("WebClient not available");
        }

        if (!oAuthService.hasValidToken()) {
            logger.error("No valid access token available for Teamleader API");
            return createErrorResponse("No valid access token available");
        }

        try {
            String accessToken = oAuthService.getAccessToken();

            // Create request body with pagination
            ObjectNode requestBody = objectMapper.createObjectNode();
            ObjectNode paginationNode = objectMapper.createObjectNode();
            paginationNode.put("size", pageSize);
            paginationNode.put("number", page);
            requestBody.set("page", paginationNode);

            logger.info("Sending WebClient request to: {}/companies.list", apiConfig.getBaseUrl());

            // Make the API call with WebClient
            JsonNode response = webClient.post()
                    .uri("/companies.list")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .retryWhen(webClientRetrySpec == null ? Retry.fixedDelay(1, java.time.Duration.ofSeconds(2))
                            .filter(ex -> !(ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest))
                            : webClientRetrySpec)
                    .doOnError(e -> logger.error("WebClient error fetching companies: {}", e.getMessage()))
                    .onErrorResume(e -> {
                        ObjectNode errorNode = objectMapper.createObjectNode();
                        errorNode.put("error", true);
                        errorNode.put("message", e.getMessage());
                        return Mono.just(errorNode);
                    })
                    .block();

            logger.info("Successfully retrieved companies with WebClient");
            return response;
        } catch (Exception e) {
            logger.error("Unexpected error using WebClient: {}", e.getMessage());
            return createErrorResponse("WebClient error: " + e.getMessage());
        }
    }

    /**
     * Get details for a specific company
     * 
     * @param companyId The Teamleader company ID
     * @return Company details
     */
    public JsonNode getCompanyDetails(String companyId) {
        if (webClient == null) {
            logger.error("WebClient not available");
            return createErrorResponse("WebClient not available");
        }

        if (!oAuthService.hasValidToken()) {
            logger.error("No valid access token available for Teamleader API");
            return createErrorResponse("No valid access token available");
        }

        try {
            String accessToken = oAuthService.getAccessToken();

            // Create request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("id", companyId);

            logger.debug("WebClient request body for companies.info: {}", requestBody);
            logger.info("Sending WebClient request to fetch company details for ID: {}", companyId);

            // Make the API call with WebClient
            JsonNode response = webClient.post()
                    .uri("/companies.info")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .retryWhen(webClientRetrySpec == null ? Retry.fixedDelay(1, java.time.Duration.ofSeconds(2))
                            .filter(ex -> !(ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest))
                            : webClientRetrySpec)
                    .doOnError(e -> logger.error("WebClient error fetching company details: {}", e.getMessage()))
                    .onErrorResume(e -> {
                        ObjectNode errorNode = objectMapper.createObjectNode();
                        errorNode.put("error", true);
                        errorNode.put("message", e.getMessage());
                        return Mono.just(errorNode);
                    })
                    .block();

            logger.info("Successfully retrieved company details with WebClient for ID: {}", companyId);
            return response;
        } catch (Exception e) {
            logger.error("Unexpected error using WebClient for company details: {}", e.getMessage());
            return createErrorResponse("WebClient error: " + e.getMessage());
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
        if (webClient == null) {
            logger.error("WebClient not available");
            return createErrorResponse("WebClient not available");
        }

        if (!oAuthService.hasValidToken()) {
            logger.error("No valid access token available for Teamleader API");
            return createErrorResponse("No valid access token available");
        }

        try {
            String accessToken = oAuthService.getAccessToken();

            // Create a simple request to get the current user
            ObjectNode requestBody = objectMapper.createObjectNode();

            logger.info("Testing API connection with WebClient to: {}/users.me", apiConfig.getBaseUrl());

            // Make the API call with WebClient
            JsonNode userData = webClient.post()
                    .uri("/users.me")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .retryWhen(webClientRetrySpec == null ? Retry.fixedDelay(1, java.time.Duration.ofSeconds(2))
                            .filter(ex -> !(ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest))
                            : webClientRetrySpec)
                    .doOnError(e -> logger.error("WebClient API connection test failed: {}", e.getMessage()))
                    .onErrorResume(e -> {
                        ObjectNode errorNode = objectMapper.createObjectNode();
                        errorNode.put("error", true);
                        errorNode.put("message", e.getMessage());
                        return Mono.just(errorNode);
                    })
                    .block();

            logger.info("API connection test with WebClient successful");

            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "success");
            result.put("message", "Successfully connected to Teamleader API using WebClient");
            result.set("data", userData);
            return result;
        } catch (Exception e) {
            logger.error("Unexpected error testing API connection with WebClient", e);
            return createErrorResponse("WebClient error: " + e.getMessage());
        }
    }
}