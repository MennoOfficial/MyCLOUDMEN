package com.cloudmen.backend.services;

import com.cloudmen.backend.config.TeamleaderApiConfig;
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

    public TeamleaderCompanyService(
            RestTemplate restTemplate,
            TeamleaderOAuthService oAuthService,
            TeamleaderApiConfig apiConfig,
            ObjectMapper objectMapper,
            TeamleaderCompanyRepository companyRepository) {
        this.restTemplate = restTemplate;
        this.oAuthService = oAuthService;
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.companyRepository = companyRepository;
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

            logger.debug("Request body for companies.list: {}", requestBody);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            String url = apiConfig.getBaseUrl() + "/companies.list";
            logger.info("Sending request to: {}", url);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    JsonNode.class);

            logger.info("Successfully retrieved companies from Teamleader API");
            logger.debug("Response body: {}", response.getBody());
            return response.getBody();
        } catch (HttpClientErrorException e) {
            logger.error("Error fetching companies from Teamleader API: {}", e.getMessage());
            logger.debug("Error response body: {}", e.getResponseBodyAsString());
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
     * Synchronize all companies from Teamleader
     * 
     * @return Summary of the synchronization
     */
    @Async
    public CompletableFuture<Map<String, Object>> syncAllCompanies() {
        logger.info("Starting synchronization of all companies from Teamleader");
        Map<String, Object> summary = new HashMap<>();

        if (!oAuthService.hasValidToken()) {
            logger.error("No valid access token available for Teamleader API");
            summary.put("success", false);
            summary.put("message", "No valid access token available");
            return CompletableFuture.completedFuture(summary);
        }

        try {
            int page = 1;
            int pageSize = 50;
            int totalCompanies = 0;
            int created = 0;
            int updated = 0;
            int errors = 0;
            boolean hasMorePages = true;

            logger.info("Starting company synchronization with page size: {}", pageSize);

            while (hasMorePages) {
                logger.info("Fetching companies page: {}", page);
                JsonNode companiesResponse = getCompanies(page, pageSize);

                if (companiesResponse == null) {
                    logger.error("Received null response when fetching companies page {}", page);
                    errors++;
                    break;
                }

                if (companiesResponse.has("error")) {
                    logger.error("Error fetching companies page {}: {}", page, companiesResponse);
                    errors++;
                    break;
                }

                JsonNode companiesData = companiesResponse.get("data");
                if (companiesData == null || !companiesData.isArray()) {
                    logger.error("Invalid data format in companies response for page {}", page);
                    errors++;
                    break;
                }

                int companiesCount = companiesData.size();
                logger.info("Retrieved {} companies on page {}", companiesCount, page);

                if (companiesCount == 0) {
                    logger.info("No more companies to fetch");
                    hasMorePages = false;
                    continue;
                }

                for (JsonNode companyNode : companiesData) {
                    try {
                        String companyId = companyNode.get("id").asText();
                        logger.debug("Processing company ID: {}", companyId);

                        JsonNode companyDetails = getCompanyDetails(companyId);

                        if (companyDetails != null && !companyDetails.has("error")) {
                            JsonNode companyData = companyDetails.get("data");
                            if (companyData != null) {
                                boolean isNew = saveCompanyFromJson(companyData);
                                if (isNew) {
                                    created++;
                                    logger.debug("Created new company: {}", companyId);
                                } else {
                                    updated++;
                                    logger.debug("Updated existing company: {}", companyId);
                                }
                                totalCompanies++;
                            } else {
                                logger.warn("No data found in company details for ID: {}", companyId);
                                errors++;
                            }
                        } else {
                            logger.error("Error fetching details for company ID {}: {}",
                                    companyId, companyDetails != null ? companyDetails.get("error") : "null response");
                            errors++;
                        }

                        // Add a small delay to avoid rate limiting
                        Thread.sleep(100);
                    } catch (Exception e) {
                        logger.error("Error processing company", e);
                        errors++;
                    }
                }

                page++;

                // Check if we've reached the end
                if (companiesCount < pageSize) {
                    logger.info("Reached last page of companies (page {})", page - 1);
                    hasMorePages = false;
                }
            }

            logger.info("Completed synchronization of companies. Total: {}, Created: {}, Updated: {}, Errors: {}",
                    totalCompanies, created, updated, errors);

            summary.put("success", errors == 0);
            summary.put("totalCompanies", totalCompanies);
            summary.put("created", created);
            summary.put("updated", updated);
            summary.put("errors", errors);
            summary.put("completedAt", LocalDateTime.now().toString());

            return CompletableFuture.completedFuture(summary);
        } catch (Exception e) {
            logger.error("Error during company synchronization", e);
            summary.put("success", false);
            summary.put("message", "Error during synchronization: " + e.getMessage());
            summary.put("error", e.getClass().getName());
            summary.put("stackTrace", e.getStackTrace()[0].toString());
            return CompletableFuture.completedFuture(summary);
        }
    }

    /**
     * Save a company from JSON data
     * 
     * @param companyData The company data from Teamleader API
     * @return true if the company was created, false if it was updated
     */
    private boolean saveCompanyFromJson(JsonNode companyData) {
        String teamleaderId = companyData.get("id").asText();
        boolean isNew = false;

        // Check if the company already exists
        Optional<TeamleaderCompany> existingCompany = companyRepository.findByTeamleaderId(teamleaderId);
        TeamleaderCompany company;

        if (existingCompany.isPresent()) {
            company = existingCompany.get();
            logger.debug("Updating existing company: {}", teamleaderId);
        } else {
            company = new TeamleaderCompany();
            company.setTeamleaderId(teamleaderId);
            company.setCreatedAt(LocalDateTime.now());
            isNew = true;
            logger.debug("Creating new company: {}", teamleaderId);
        }

        // Update company data
        company.setName(companyData.get("name").asText());

        // VAT number
        if (companyData.has("vat_number") && !companyData.get("vat_number").isNull()) {
            company.setVatNumber(companyData.get("vat_number").asText());
        }

        // Website
        if (companyData.has("website") && !companyData.get("website").isNull()) {
            company.setWebsite(companyData.get("website").asText());
        }

        // Business type
        if (companyData.has("business_type") && !companyData.get("business_type").isNull()) {
            company.setBusinessType(companyData.get("business_type").get("type").asText());
        }

        // Primary address
        if (companyData.has("addresses") && companyData.get("addresses").isArray()
                && companyData.get("addresses").size() > 0) {
            JsonNode addressNode = companyData.get("addresses").get(0);
            TeamleaderCompany.Address address = new TeamleaderCompany.Address();

            if (addressNode.has("type") && !addressNode.get("type").isNull()) {
                address.setType(addressNode.get("type").asText());
            }

            if (addressNode.has("line_1") && !addressNode.get("line_1").isNull()) {
                address.setLine1(addressNode.get("line_1").asText());
            }

            if (addressNode.has("line_2") && !addressNode.get("line_2").isNull()) {
                address.setLine2(addressNode.get("line_2").asText());
            }

            if (addressNode.has("postal_code") && !addressNode.get("postal_code").isNull()) {
                address.setPostalCode(addressNode.get("postal_code").asText());
            }

            if (addressNode.has("city") && !addressNode.get("city").isNull()) {
                address.setCity(addressNode.get("city").asText());
            }

            if (addressNode.has("country") && !addressNode.get("country").isNull()) {
                address.setCountry(addressNode.get("country").asText());
            }

            company.setPrimaryAddress(address);
        }

        // Contact info (emails, phones, etc.)
        List<TeamleaderCompany.ContactInfo> contactInfoList = new ArrayList<>();

        // Emails
        if (companyData.has("emails") && companyData.get("emails").isArray()) {
            for (JsonNode emailNode : companyData.get("emails")) {
                if (emailNode.has("type") && emailNode.has("email")) {
                    String type = emailNode.get("type").asText();
                    String email = emailNode.get("email").asText();
                    contactInfoList.add(new TeamleaderCompany.ContactInfo("email-" + type, email));
                }
            }
        }

        // Telephones
        if (companyData.has("telephones") && companyData.get("telephones").isArray()) {
            for (JsonNode phoneNode : companyData.get("telephones")) {
                if (phoneNode.has("type") && phoneNode.has("number")) {
                    String type = phoneNode.get("type").asText();
                    String number = phoneNode.get("number").asText();
                    contactInfoList.add(new TeamleaderCompany.ContactInfo("phone-" + type, number));
                }
            }
        }

        company.setContactInfo(contactInfoList);

        // Custom fields (if any)
        if (companyData.has("custom_fields") && companyData.get("custom_fields").isArray()) {
            Map<String, Object> customFields = new HashMap<>();

            for (JsonNode fieldNode : companyData.get("custom_fields")) {
                if (fieldNode.has("id") && fieldNode.has("value")) {
                    String id = fieldNode.get("id").asText();
                    JsonNode valueNode = fieldNode.get("value");

                    if (valueNode.isTextual()) {
                        customFields.put(id, valueNode.asText());
                    } else if (valueNode.isNumber()) {
                        customFields.put(id, valueNode.asDouble());
                    } else if (valueNode.isBoolean()) {
                        customFields.put(id, valueNode.asBoolean());
                    } else {
                        customFields.put(id, valueNode.toString());
                    }
                }
            }

            company.setCustomFields(customFields);
        }

        // Update timestamps
        company.setUpdatedAt(LocalDateTime.now());
        company.setSyncedAt(LocalDateTime.now());

        // Save to database
        companyRepository.save(company);

        return isNew;
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
     * Create HTTP headers with authorization
     * 
     * @param accessToken The OAuth access token
     * @return HTTP headers
     */
    private HttpHeaders createHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return headers;
    }

    /**
     * Handle API errors
     * 
     * @param e The HTTP client error
     * @return Error response as JsonNode
     */
    private JsonNode handleApiError(HttpClientErrorException e) {
        try {
            // Try to parse the error response from Teamleader
            JsonNode errorResponse = objectMapper.readTree(e.getResponseBodyAsString());
            return errorResponse;
        } catch (Exception ex) {
            // If we can't parse the error, create a generic error response
            return createErrorResponse("API Error: " + e.getStatusCode() + " - " + e.getMessage());
        }
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
            logger.debug("Response body: {}", response.getBody());

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