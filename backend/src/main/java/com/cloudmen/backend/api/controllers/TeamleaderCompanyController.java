package com.cloudmen.backend.api.controllers;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
    private final TeamleaderCompanyRepository companyRepository;

    public TeamleaderCompanyController(
            TeamleaderCompanyService companyService,
            TeamleaderOAuthService oAuthService,
            ObjectMapper objectMapper,
            TeamleaderCompanyRepository companyRepository) {
        this.companyService = companyService;
        this.oAuthService = oAuthService;
        this.objectMapper = objectMapper;
        this.companyRepository = companyRepository;

        // Log the base path that this controller is mapped to
        logger.info("TeamleaderCompanyController initialized with base path: /api/teamleader/companies");
    }

    /**
     * Get a list of companies from Teamleader API
     * 
     * @param page     Page number (1-based)
     * @param pageSize Number of items per page
     * @return List of companies
     */
    @GetMapping("/remote")
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
    @GetMapping("/remote/{id}")
    public ResponseEntity<JsonNode> getCompanyDetailsFromApi(@PathVariable("id") String id) {
        logger.info("Fetching company details from Teamleader API for ID: {}", id);

        if (!oAuthService.hasValidToken()) {
            return ResponseEntity.ok(createAuthRequiredResponse());
        }

        JsonNode companyDetails = companyService.getCompanyDetails(id);
        return ResponseEntity.ok(companyDetails);
    }

    /**
     * Get a list of companies from the local database with simplified DTOs
     * 
     * @param page      Page number (0-based)
     * @param size      Number of items per page
     * @param sortBy    Field to sort by
     * @param direction Sort direction (asc or desc)
     * @return List of company DTOs
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getCompanies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        logger.info("Fetching companies from local database, page: {}, size: {}", page, size);

        try {

            List<TeamleaderCompany> companies = companyService.getAllCompanies();

            // Convert entities to DTOs
            List<CompanyListDTO> companyDtos = companies.stream()
                    .map(CompanyListDTO::fromEntity)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("companies", companyDtos);
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
            // Convert entity to detailed DTO
            CompanyDetailDTO companyDetailDTO = CompanyDetailDTO.fromEntity(company);
            return ResponseEntity.ok(companyDetailDTO);
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

            // Convert entities to DTOs
            List<CompanyListDTO> companyDtos = StreamSupport.stream(companies.spliterator(), false)
                    .map(CompanyListDTO::fromEntity)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(companyDtos);
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
     * Update the status of a company
     * 
     * @param id           The Teamleader company ID
     * @param statusUpdate Status update data containing new status
     * @return Updated company details
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<Object> updateCompanyStatus(
            @PathVariable("id") String id,
            @RequestBody Map<String, String> statusUpdate) {

        logger.info("Received request to update status for company ID: {}", id);
        logger.info("Full request body: {}", statusUpdate);

        // First check if this API endpoint is actually being called
        logger.info("====== STATUS UPDATE API CALL RECEIVED ======");
        logger.info("ID from path: {}", id);
        logger.info("Request body: {}", statusUpdate);

        String newStatusStr = statusUpdate.get("status");
        if (newStatusStr == null) {
            logger.warn("Status field is missing in the request body");
            Map<String, Object> response = new HashMap<>();
            response.put("error", true);
            response.put("message", "Status field is required");
            return ResponseEntity.badRequest().body(response);
        }

        CompanyStatusType newStatus;
        try {
            // Try to parse the status string to enum
            newStatus = CompanyStatusType.valueOf(newStatusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid status value: {}", newStatusStr);
            Map<String, Object> response = new HashMap<>();
            response.put("error", true);
            response.put("message", "Invalid status value. Allowed values: "
                    + String.join(", ",
                            java.util.Arrays.stream(CompanyStatusType.values())
                                    .map(Enum::name)
                                    .collect(Collectors.toList())));
            return ResponseEntity.badRequest().body(response);
        }

        logger.info("Processing status change to: {}", newStatus);

        try {
            // Try first by MongoDB ID to match what frontend sends
            logger.info("Attempting to look up company by id (could be either MongoDB _id or teamleaderId)");
            TeamleaderCompany company = null;

            // First try MongoDB ID (which is what we send in the DTO)
            try {
                Optional<TeamleaderCompany> companyById = companyRepository.findById(id);
                if (companyById.isPresent()) {
                    company = companyById.get();
                    logger.info("Found company by MongoDB _id: {}, name: {}", id, company.getName());
                } else {
                    logger.info("Company not found by MongoDB _id, trying teamleaderId");
                }
            } catch (Exception e) {
                logger.warn("Error looking up by MongoDB ID (probably not a valid ObjectId): {}", e.getMessage());
            }

            // If not found by MongoDB ID, try teamleaderId
            if (company == null) {
                company = companyService.getCompanyByTeamleaderId(id);
            }

            if (company == null) {
                logger.warn("Company not found with ID: {}", id);
                Map<String, Object> response = new HashMap<>();
                response.put("error", true);
                response.put("message", "Company not found with ID: " + id);
                return ResponseEntity.notFound().build();
            }

            logger.info("Found company: {}, current status: {}", company.getName(),
                    company.getStatus() != null ? company.getStatus() : "undefined");

            // Update the company's status directly in the entity
            company.setStatus(newStatus);

            // Also maintain status in customFields for backward compatibility
            Map<String, Object> customFields = company.getCustomFields();
            if (customFields == null) {
                logger.info("Creating new customFields map for company");
                customFields = new HashMap<>();
                company.setCustomFields(customFields);
            }
            customFields.put("status", newStatus.name());
            logger.info("Updated status to: {}", newStatus);

            // Save the updated company
            company = companyService.saveCompany(company);
            logger.info("Company saved successfully");

            // Return the updated company
            CompanyDetailDTO updatedCompany = CompanyDetailDTO.fromEntity(company);
            logger.info("Returning updated company with status: {}", updatedCompany.getStatus());
            return ResponseEntity.ok(updatedCompany);
        } catch (Exception e) {
            logger.error("Error updating company status", e);
            Map<String, Object> response = new HashMap<>();
            response.put("error", true);
            response.put("message", "Error updating company status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get companies by status
     * 
     * @param status The status to filter by
     * @return List of companies with the specified status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<Object> getCompaniesByStatus(@PathVariable("status") String status) {
        logger.info("Retrieving companies with status: {}", status);

        CompanyStatusType statusType;
        try {
            statusType = CompanyStatusType.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid status value: {}", status);
            Map<String, Object> response = new HashMap<>();
            response.put("error", true);
            response.put("message", "Invalid status value. Allowed values: "
                    + String.join(", ",
                            java.util.Arrays.stream(CompanyStatusType.values())
                                    .map(Enum::name)
                                    .collect(Collectors.toList())));
            return ResponseEntity.badRequest().body(response);
        }

        try {
            List<TeamleaderCompany> companies = companyRepository.findAll();

            // Filter companies by status
            List<TeamleaderCompany> filteredCompanies = companies.stream()
                    .filter(company -> company.getStatus() == statusType)
                    .collect(Collectors.toList());

            List<CompanyListDTO> companyDtos = CompanyListDTO.fromEntities(filteredCompanies);

            Map<String, Object> response = new HashMap<>();
            response.put("companies", companyDtos);
            response.put("count", companyDtos.size());
            response.put("status", statusType.name());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving companies by status", e);
            Map<String, Object> response = new HashMap<>();
            response.put("error", true);
            response.put("message", "Error retrieving companies: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
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