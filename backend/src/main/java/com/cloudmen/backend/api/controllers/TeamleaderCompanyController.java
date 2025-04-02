package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.api.dtos.CompanyDetailDTO;
import com.cloudmen.backend.api.dtos.CompanyListDTO;
import com.cloudmen.backend.domain.models.TeamleaderCompany;
import com.cloudmen.backend.repositories.TeamleaderCompanyRepository;
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
import org.springframework.data.domain.Page;
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
            Sort.Direction sortDirection = direction.equalsIgnoreCase("desc") ? Sort.Direction.DESC
                    : Sort.Direction.ASC;

            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

            // Use the repository's findAllWithMyCLOUDMENAccess instead of findAll
            org.springframework.data.domain.Page<TeamleaderCompany> companiesPage = companyRepository
                    .findAllWithMyCLOUDMENAccess(pageable);

            // Get content and total count from the page object
            List<TeamleaderCompany> companies = companiesPage.getContent();
            long totalItems = companiesPage.getTotalElements();

            logger.info("Retrieved {} companies from database, total count: {}", companies.size(), totalItems);

            // Convert entities to DTOs
            List<CompanyListDTO> companyDtos = companies.stream()
                    .map(CompanyListDTO::fromEntity)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("companies", companyDtos);
            response.put("currentPage", page);
            response.put("totalItems", totalItems);
            response.put("totalPages", companiesPage.getTotalPages());

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
     * @param page  Page number (0-based)
     * @param size  Number of items per page
     * @return List of matching companies
     */
    @GetMapping("/search")
    public ResponseEntity<Object> searchCompanies(
            @RequestParam("query") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        logger.info("Searching companies with query: '{}', page: {}, size: {}", query, page, size);

        try {
            Pageable pageable = PageRequest.of(page, size);
            // Use the repository method that filters by MyCLOUDMEN access
            Page<TeamleaderCompany> companiesPage = companyRepository
                    .findByNameContainingIgnoreCaseWithMyCLOUDMENAccess(query, pageable);

            List<TeamleaderCompany> companies = companiesPage.getContent();
            long totalItems = companiesPage.getTotalElements();

            logger.info("Found {} matching companies, total count: {}", companies.size(), totalItems);

            // Convert entities to DTOs
            List<CompanyListDTO> companyDtos = companies.stream()
                    .map(CompanyListDTO::fromEntity)
                    .collect(Collectors.toList());

            // Create response with pagination info
            Map<String, Object> response = new HashMap<>();
            response.put("companies", companyDtos);
            response.put("currentPage", page);
            response.put("totalItems", totalItems);
            response.put("totalPages", companiesPage.getTotalPages());

            return ResponseEntity.ok(response);
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

        String newStatus = statusUpdate.get("status");
        if (newStatus == null) {
            logger.warn("Status field is missing in the request body");
            Map<String, Object> response = new HashMap<>();
            response.put("error", true);
            response.put("message", "Status field is required");
            return ResponseEntity.badRequest().body(response);
        }

        // Validate status value
        if (!newStatus.equals("Active") && !newStatus.equals("Inactive")) {
            logger.warn("Invalid status value: {}", newStatus);
            Map<String, Object> response = new HashMap<>();
            response.put("error", true);
            response.put("message", "Status must be either 'Active' or 'Inactive'");
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
                    company.getCustomFields() != null && company.getCustomFields().containsKey("status")
                            ? company.getCustomFields().get("status")
                            : "undefined");

            // Update the company's status
            Map<String, Object> customFields = company.getCustomFields();
            if (customFields == null) {
                logger.info("Creating new customFields map for company");
                customFields = new HashMap<>();
                company.setCustomFields(customFields);
            }

            // Store status in customFields - this is how the status is managed in this
            // application
            customFields.put("status", newStatus);
            logger.info("Updated customFields with new status");

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
     * Test endpoint to verify the status path routing works
     */
    @GetMapping("/{id}/status/test")
    public ResponseEntity<Object> testStatusEndpoint(@PathVariable("id") String id) {
        logger.info("Test status endpoint reached for ID: {}", id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Status endpoint test successful");
        response.put("id", id);
        return ResponseEntity.ok(response);
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