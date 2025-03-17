package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.domain.models.TeamleaderCompany;
import com.cloudmen.backend.services.TeamleaderCompanyService;
import com.cloudmen.backend.services.TeamleaderOAuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for exposing Teamleader company data
 */
@RestController
@RequestMapping("/api/teamleader/companies")
public class TeamleaderCompanyController {

    private static final Logger logger = LoggerFactory.getLogger(TeamleaderCompanyController.class);
    private final TeamleaderCompanyService companyService;
    private final TeamleaderOAuthService oAuthService;
    private final ObjectMapper objectMapper;

    public TeamleaderCompanyController(
            TeamleaderCompanyService companyService,
            TeamleaderOAuthService oAuthService,
            ObjectMapper objectMapper) {
        this.companyService = companyService;
        this.oAuthService = oAuthService;
        this.objectMapper = objectMapper;
    }

    /**
     * Get a list of companies from Teamleader API
     * 
     * @param page     Page number (1-based)
     * @param pageSize Number of items per page
     * @return List of companies
     */
    @GetMapping("/api")
    public ResponseEntity<JsonNode> getCompaniesFromApi(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {

        logger.info("Fetching companies from Teamleader API, page: {}, pageSize: {}", page, pageSize);

        if (!oAuthService.hasValidToken()) {
            logger.warn("No valid access token available for Teamleader API");
            return ResponseEntity.ok(createAuthRequiredResponse());
        }

        try {
            JsonNode companies = companyService.getCompanies(page, pageSize);

            if (companies.has("error")) {
                logger.error("Error fetching companies from API: {}",
                        companies.has("message") ? companies.get("message").asText() : "Unknown error");
                return ResponseEntity.ok(companies);
            }

            logger.info("Successfully fetched companies from Teamleader API");
            if (companies.has("data") && companies.get("data").isArray()) {
                logger.info("Retrieved {} companies", companies.get("data").size());
            }

            return ResponseEntity.ok(companies);
        } catch (Exception e) {
            logger.error("Unexpected error fetching companies from API", e);
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", true);
            errorResponse.put("message", "Unexpected error: " + e.getMessage());
            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Get details for a specific company from Teamleader API
     * 
     * @param id The Teamleader company ID
     * @return Company details
     */
    @GetMapping("/api/{id}")
    public ResponseEntity<JsonNode> getCompanyDetailsFromApi(@PathVariable("id") String id) {
        logger.info("Fetching company details from Teamleader API for ID: {}", id);

        if (!oAuthService.hasValidToken()) {
            return ResponseEntity.ok(createAuthRequiredResponse());
        }

        JsonNode companyDetails = companyService.getCompanyDetails(id);
        return ResponseEntity.ok(companyDetails);
    }

    /**
     * Get a list of companies from the local database
     * 
     * @param page      Page number (0-based)
     * @param size      Number of items per page
     * @param sortBy    Field to sort by
     * @param direction Sort direction (asc or desc)
     * @return List of companies
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getCompanies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        logger.info("Fetching companies from local database, page: {}, size: {}", page, size);

        try {
            Sort.Direction sortDirection = direction.equalsIgnoreCase("desc") ? Sort.Direction.DESC
                    : Sort.Direction.ASC;

            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
            List<TeamleaderCompany> companies = companyService.getAllCompanies();

            Map<String, Object> response = new HashMap<>();
            response.put("companies", companies);
            response.put("currentPage", page);
            response.put("totalItems", companies.size());
            response.put("totalPages", (int) Math.ceil((double) companies.size() / size));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching companies from database", e);
            Map<String, Object> response = new HashMap<>();
            response.put("error", true);
            response.put("message", "Error fetching companies: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Get details for a specific company from the local database
     * 
     * @param id The Teamleader company ID
     * @return Company details
     */
    @GetMapping("/{id}")
    public ResponseEntity<Object> getCompanyDetails(@PathVariable("id") String id) {
        logger.info("Fetching company details from local database for ID: {}", id);

        TeamleaderCompany company = companyService.getCompanyByTeamleaderId(id);

        if (company != null) {
            return ResponseEntity.ok(company);
        } else {
            Map<String, Object> response = new HashMap<>();
            response.put("error", true);
            response.put("message", "Company not found with ID: " + id);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Search for companies by name
     * 
     * @param query The search query
     * @return List of matching companies
     */
    @GetMapping("/search")
    public ResponseEntity<Object> searchCompanies(@RequestParam("query") String query) {
        logger.info("Searching companies with query: {}", query);

        try {
            Iterable<TeamleaderCompany> companies = companyService.searchCompaniesByName(query);
            return ResponseEntity.ok(companies);
        } catch (Exception e) {
            logger.error("Error searching companies", e);
            Map<String, Object> response = new HashMap<>();
            response.put("error", true);
            response.put("message", "Error searching companies: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Test the connection to the Teamleader API
     * 
     * @return Connection test results
     */
    @GetMapping("/test-connection")
    public ResponseEntity<JsonNode> testConnection() {
        logger.info("Testing connection to Teamleader API");

        if (!oAuthService.hasValidToken()) {
            return ResponseEntity.ok(createAuthRequiredResponse());
        }

        JsonNode result = companyService.testApiConnection();

        logger.info("Connection test completed with status: {}",
                result.has("error") ? "error" : "success");

        return ResponseEntity.ok(result);
    }

    /**
     * Create a response indicating that authorization is required
     * 
     * @return JSON response with auth instructions
     */
    private JsonNode createAuthRequiredResponse() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("error", true);
        response.put("message", "Teamleader API authorization required");
        response.put("authUrl", "/api/teamleader/oauth/authorize");
        response.put("statusUrl", "/api/teamleader/oauth/status");

        return response;
    }
}