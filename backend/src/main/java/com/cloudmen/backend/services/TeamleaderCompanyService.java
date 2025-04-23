package com.cloudmen.backend.services;

import com.cloudmen.backend.config.TeamleaderApiConfig;
import com.cloudmen.backend.domain.models.TeamleaderCompany;
import com.cloudmen.backend.repositories.TeamleaderCompanyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.LocalDateTime;
import java.util.*;

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
    private final WebClient webClient;
    private final Retry webClientRetrySpec;

    public TeamleaderCompanyService(
            TeamleaderOAuthService oAuthService,
            TeamleaderApiConfig apiConfig,
            ObjectMapper objectMapper,
            TeamleaderCompanyRepository companyRepository,
            WebClient webClient,
            @Qualifier("webClientRetrySpec") Retry webClientRetrySpec) {
        this.oAuthService = oAuthService;
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.companyRepository = companyRepository;
        this.webClient = webClient;
        this.webClientRetrySpec = webClientRetrySpec;
        logger.info("TeamleaderCompanyService initialized");
    }

    /**
     * Get a list of companies from Teamleader API
     * 
     * @param page     Page number (1-based)
     * @param pageSize Number of items per page
     * @return List of companies as JsonNode
     */
    public JsonNode getCompanies(int page, int pageSize) {
        if (webClient == null || !oAuthService.hasValidToken()) {
            return createErrorResponse("API client not available or no valid token");
        }

        try {
            String accessToken = oAuthService.getAccessToken();

            // Create request body with pagination
            ObjectNode requestBody = objectMapper.createObjectNode();
            ObjectNode paginationNode = objectMapper.createObjectNode();
            paginationNode.put("size", pageSize);
            paginationNode.put("number", page);
            requestBody.set("page", paginationNode);

            // Request custom fields explicitly
            requestBody.set("includes", objectMapper.createArrayNode().add("custom_fields"));

            logger.info("Fetching companies page: {}", page);

            // Make the API call
            return webClient.post()
                    .uri("/companies.list")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .retryWhen(webClientRetrySpec)
                    .onErrorResume(e -> {
                        logger.error("Error fetching companies: {}", e.getMessage());
                        return Mono.just(createErrorResponse(e.getMessage()));
                    })
                    .block();
        } catch (Exception e) {
            logger.error("Unexpected error fetching companies: {}", e.getMessage());
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * Get details for a specific company
     * 
     * @param companyId The Teamleader company ID
     * @return Company details as JsonNode
     */
    public JsonNode getCompanyDetails(String companyId) {
        if (webClient == null || !oAuthService.hasValidToken()) {
            return createErrorResponse("API client not available or no valid token");
        }

        try {
            String accessToken = oAuthService.getAccessToken();

            // Create request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("id", companyId);
            requestBody.set("includes", objectMapper.createArrayNode().add("custom_fields"));

            logger.info("Fetching details for company ID: {}", companyId);

            // Make the API call
            JsonNode response = webClient.post()
                    .uri("/companies.info")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .retryWhen(webClientRetrySpec)
                    .onErrorResume(e -> {
                        logger.error("Error fetching company details: {}", e.getMessage());
                        return Mono.just(createErrorResponse(e.getMessage()));
                    })
                    .block();

            if (response != null && response.has("data") && !response.get("data").has("custom_fields")) {
                logger.warn("No custom fields found for company: {}", companyId);
            }

            return response;
        } catch (Exception e) {
            logger.error("Unexpected error fetching company details: {}", e.getMessage());
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * Test the connection to the Teamleader API
     * 
     * @return Connection status as JsonNode
     */
    public JsonNode testApiConnection() {
        if (webClient == null || !oAuthService.hasValidToken()) {
            return createErrorResponse("API client not available or no valid token");
        }

        try {
            String accessToken = oAuthService.getAccessToken();
            ObjectNode requestBody = objectMapper.createObjectNode();

            logger.info("Testing API connection");

            // Make a simple call to users.me endpoint
            JsonNode userData = webClient.post()
                    .uri("/users.me")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .retryWhen(webClientRetrySpec)
                    .onErrorResume(e -> {
                        logger.error("API connection test failed: {}", e.getMessage());
                        return Mono.just(createErrorResponse(e.getMessage()));
                    })
                    .block();

            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "success");
            result.put("message", "Successfully connected to Teamleader API");
            result.set("data", userData);
            return result;
        } catch (Exception e) {
            logger.error("Unexpected error testing API connection: {}", e.getMessage());
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * Get all companies from the local database
     * 
     * @return List of companies
     */
    public List<TeamleaderCompany> getAllCompanies() {
        return companyRepository.findAll();
    }

    /**
     * Get a company by its Teamleader ID from the local database
     * 
     * @param teamleaderId The Teamleader ID
     * @return The company, or null if not found
     */
    public TeamleaderCompany getCompanyByTeamleaderId(String teamleaderId) {
        // First try by teamleaderId
        Optional<TeamleaderCompany> company = companyRepository.findByTeamleaderId(teamleaderId);

        if (company.isPresent()) {
            return company.get();
        }

        // If not found and it looks like a MongoDB ID, try by ID
        if (teamleaderId.length() == 24) {
            try {
                return companyRepository.findById(teamleaderId).orElse(null);
            } catch (Exception e) {
                logger.warn("Error looking up by MongoDB ID: {}", e.getMessage());
            }
        }

        return null;
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
        company.setUpdatedAt(LocalDateTime.now());
        logger.info("Saving company: {}", company.getName());
        return companyRepository.save(company);
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
}