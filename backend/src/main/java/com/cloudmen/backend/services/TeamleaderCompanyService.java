package com.cloudmen.backend.services;

import com.cloudmen.backend.config.TeamleaderApiConfig;
import com.cloudmen.backend.domain.models.TeamleaderCompany;
import com.cloudmen.backend.domain.enums.CompanyStatusType;
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
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
    public List<TeamleaderCompany> searchCompaniesByName(String name) {
        Iterable<TeamleaderCompany> results = companyRepository.findByNameContainingIgnoreCase(name);
        return StreamSupport.stream(results.spliterator(), false)
                .collect(Collectors.toList());
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
     * Update a company's status
     * 
     * @param id        The company ID (MongoDB _id or teamleaderId)
     * @param newStatus The new status to set
     * @return The updated company, or empty if not found
     */
    public Optional<TeamleaderCompany> updateCompanyStatus(String id, CompanyStatusType newStatus) {
        logger.info("Updating status for company ID: {} to: {}", id, newStatus);

        TeamleaderCompany company = null;

        // First try MongoDB ID
        try {
            Optional<TeamleaderCompany> companyById = companyRepository.findById(id);
            if (companyById.isPresent()) {
                company = companyById.get();
                logger.info("Found company by MongoDB _id: {}, name: {}", id, company.getName());
            } else {
                logger.info("Company not found by MongoDB _id, trying teamleaderId");
            }
        } catch (Exception e) {
            logger.warn("Error looking up by MongoDB ID: {}", e.getMessage());
        }

        // If not found by MongoDB ID, try teamleaderId
        if (company == null) {
            company = getCompanyByTeamleaderId(id);
        }

        if (company == null) {
            logger.warn("Company not found with ID: {}", id);
            return Optional.empty();
        }

        logger.info("Found company: {}, current status: {}", company.getName(),
                company.getStatus() != null ? company.getStatus() : "undefined");

        // Update the company's status directly in the entity
        company.setStatus(newStatus);

        // Also maintain status in customFields for backward compatibility
        Map<String, Object> customFields = company.getCustomFields();
        if (customFields == null) {
            customFields = new HashMap<>();
            company.setCustomFields(customFields);
        }
        customFields.put("status", newStatus.name());
        logger.info("Updated status to: {}", newStatus);

        // Save the updated company
        company = saveCompany(company);
        logger.info("Company saved successfully");

        return Optional.of(company);
    }

    /**
     * Get companies by status
     * 
     * @param status The status to filter by
     * @return List of companies with the specified status
     */
    public List<TeamleaderCompany> getCompaniesByStatus(CompanyStatusType status) {
        logger.info("Retrieving companies with status: {}", status);

        List<TeamleaderCompany> companies = companyRepository.findAll();

        // Filter companies by status
        return companies.stream()
                .filter(company -> company.getStatus() == status)
                .collect(Collectors.toList());
    }

    /**
     * Find a company by domain
     * 
     * @param domain The domain to search for
     * @return The company with the specified domain, if found
     */
    public Optional<TeamleaderCompany> findCompanyByDomain(String domain) {
        logger.info("Finding company by domain: {}", domain);

        // Search all companies
        List<TeamleaderCompany> allCompanies = getAllCompanies();

        // Find first company where any of the emails contains the domain
        return allCompanies.stream()
                .filter(company -> matchesDomain(company, domain))
                .findFirst();
    }

    /**
     * Helper method to check if a company matches a domain
     * 
     * @param company The company to check
     * @param domain  The domain to match
     * @return true if the company matches the domain
     */
    private boolean matchesDomain(TeamleaderCompany company, String domain) {
        // Check all contact info for email type
        if (company.getContactInfo() != null) {
            boolean emailMatch = company.getContactInfo().stream()
                    .filter(contact -> "email".equalsIgnoreCase(contact.getType()))
                    .anyMatch(contact -> contact.getValue() != null &&
                            contact.getValue().contains("@" + domain));

            if (emailMatch) {
                return true;
            }
        }

        // Check website domain
        if (company.getWebsite() != null && company.getWebsite().contains(domain)) {
            return true;
        }

        // Check custom emails if they exist
        if (company.getCustomFields() != null &&
                company.getCustomFields().containsKey("contacts")) {
            try {
                // Parse contacts as JSON array if possible
                JsonNode contacts = objectMapper.readTree(
                        company.getCustomFields().get("contacts").toString());

                if (contacts.isArray()) {
                    for (JsonNode contact : contacts) {
                        if (contact.has("email") &&
                                contact.get("email").asText().contains("@" + domain)) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                // Silently handle parsing errors
                logger.debug("Error parsing contacts for company {}: {}",
                        company.getName(), e.getMessage());
            }
        }

        return false;
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