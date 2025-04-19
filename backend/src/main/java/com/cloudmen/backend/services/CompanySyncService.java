package com.cloudmen.backend.services;

import com.cloudmen.backend.domain.models.TeamleaderCompany;
import com.cloudmen.backend.repositories.TeamleaderCompanyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for synchronizing company data between Teamleader API and local
 * database
 */
@Service
public class CompanySyncService {

    private static final Logger logger = LoggerFactory.getLogger(CompanySyncService.class);
    private final TeamleaderCompanyService companyService;
    private final TeamleaderCompanyRepository companyRepository;
    private final UserSyncService userSyncService;

    public CompanySyncService(
            TeamleaderCompanyService companyService,
            TeamleaderCompanyRepository companyRepository,
            UserSyncService userSyncService) {
        this.companyService = companyService;
        this.companyRepository = companyRepository;
        this.userSyncService = userSyncService;
        logger.info("CompanySyncService initialized");
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
                JsonNode companiesResponse = companyService.getCompanies(page, pageSize);

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
                totalCompanies += companiesCount;

                // Process each company
                for (JsonNode companyData : companiesData) {
                    try {
                        String companyId = companyData.get("id").asText();

                        // Get detailed company info
                        JsonNode companyDetails = companyService.getCompanyDetails(companyId);

                        if (companyDetails == null || companyDetails.has("error")) {
                            logger.error("Failed to fetch details for company ID: {}", companyId);
                            errors++;
                            continue;
                        }

                        // Save the company data
                        boolean isNew = processSingleCompany(companyDetails.get("data"));
                        if (isNew) {
                            created++;
                        } else {
                            updated++;
                        }
                    } catch (Exception e) {
                        logger.error("Error processing company data: {}", e.getMessage());
                        errors++;
                    }
                }

                // Check if there are more pages
                hasMorePages = companiesCount == pageSize;
                page++;
            }

            // Update summary with results
            summary.put("success", true);
            summary.put("totalCompanies", totalCompanies);
            summary.put("created", created);
            summary.put("updated", updated);
            summary.put("errors", errors);
            summary.put("timestamp", LocalDateTime.now().toString());

            logger.info("Company synchronization completed: {} total, {} created, {} updated, {} errors",
                    totalCompanies, created, updated, errors);

            // After sync is complete, update user roles
            userSyncService.updateExistingUserRoles();

            return CompletableFuture.completedFuture(summary);
        } catch (Exception e) {
            logger.error("Error during company synchronization", e);
            summary.put("success", false);
            summary.put("error", e.getMessage());
            summary.put("timestamp", LocalDateTime.now().toString());
            return CompletableFuture.completedFuture(summary);
        }
    }

    /**
     * Process a single company from JSON data
     * 
     * @param companyData Company data from Teamleader API
     * @return true if new company was created, false if existing company was
     *         updated
     */
    private boolean processSingleCompany(JsonNode companyData) {
        if (companyData == null) {
            logger.warn("Skipping null company data");
            return false;
        }

        try {
            String teamleaderId = companyData.get("id").asText();
            String name = companyData.get("name").asText();

            logger.info("Processing company: {} (ID: {})", name, teamleaderId);

            // Check if company already exists
            TeamleaderCompany existingCompany = companyRepository.findByTeamleaderId(teamleaderId).orElse(null);
            boolean isNew = existingCompany == null;

            // Create new company or update existing one
            TeamleaderCompany company = isNew ? new TeamleaderCompany() : existingCompany;

            // Set basic fields
            company.setTeamleaderId(teamleaderId);
            company.setName(name);

            // Set additional fields if available
            if (companyData.has("website")) {
                company.setWebsite(companyData.get("website").asText());
            }

            if (companyData.has("vat_number")) {
                company.setVatNumber(companyData.get("vat_number").asText());
            }

            // Set last synced timestamp
            company.setSyncedAt(LocalDateTime.now());

            // Save to repository
            companyRepository.save(company);

            logger.info("Successfully {} company: {}", isNew ? "created" : "updated", name);
            return isNew;
        } catch (Exception e) {
            logger.error("Error processing company: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Refresh custom fields for all companies
     * 
     * @return Summary of the refresh operation
     */
    @Async
    public CompletableFuture<Map<String, Object>> refreshCustomFields() {
        logger.info("Starting refresh of custom fields for all companies");
        Map<String, Object> summary = new HashMap<>();

        try {
            int total = 0;
            int updated = 0;
            int errors = 0;

            // Get all companies
            Iterable<TeamleaderCompany> companies = companyRepository.findAll();

            for (TeamleaderCompany company : companies) {
                total++;

                try {
                    // Get fresh data from API
                    JsonNode companyDetails = companyService.getCompanyDetails(company.getTeamleaderId());

                    if (companyDetails == null || companyDetails.has("error")) {
                        logger.error("Failed to fetch details for company: {}", company.getName());
                        errors++;
                        continue;
                    }

                    JsonNode companyData = companyDetails.get("data");

                    // Update custom fields
                    if (companyData.has("custom_fields")) {
                        Map<String, Object> customFields = new HashMap<>();

                        JsonNode customFieldsNode = companyData.get("custom_fields");
                        customFieldsNode.fields().forEachRemaining(entry -> {
                            String key = entry.getKey();
                            JsonNode value = entry.getValue();

                            if (value.isTextual()) {
                                customFields.put(key, value.asText());
                            } else if (value.isNumber()) {
                                customFields.put(key, value.asDouble());
                            } else if (value.isBoolean()) {
                                customFields.put(key, value.asBoolean());
                            } else {
                                customFields.put(key, value.toString());
                            }
                        });

                        company.setCustomFields(customFields);
                        companyRepository.save(company);
                        updated++;

                        logger.info("Updated custom fields for company: {}", company.getName());
                    }
                } catch (Exception e) {
                    logger.error("Error updating custom fields for company {}: {}",
                            company.getName(), e.getMessage());
                    errors++;
                }
            }

            // Update summary
            summary.put("success", true);
            summary.put("totalCompanies", total);
            summary.put("updated", updated);
            summary.put("errors", errors);
            summary.put("timestamp", LocalDateTime.now().toString());

            logger.info("Custom fields refresh completed: {} total, {} updated, {} errors",
                    total, updated, errors);

            return CompletableFuture.completedFuture(summary);
        } catch (Exception e) {
            logger.error("Error during custom fields refresh", e);
            summary.put("success", false);
            summary.put("error", e.getMessage());
            summary.put("timestamp", LocalDateTime.now().toString());
            return CompletableFuture.completedFuture(summary);
        }
    }
}