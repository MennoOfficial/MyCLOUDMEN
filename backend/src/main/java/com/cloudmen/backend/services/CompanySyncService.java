package com.cloudmen.backend.services;

import com.cloudmen.backend.domain.models.TeamleaderCompany;
import com.cloudmen.backend.repositories.TeamleaderCompanyRepository;
import com.cloudmen.backend.config.TeamleaderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final TeamleaderConfig teamleaderConfig;

    public CompanySyncService(
            TeamleaderCompanyService companyService,
            TeamleaderCompanyRepository companyRepository,
            UserSyncService userSyncService,
            TeamleaderConfig teamleaderConfig) {
        this.companyService = companyService;
        this.companyRepository = companyRepository;
        this.userSyncService = userSyncService;
        this.teamleaderConfig = teamleaderConfig;
    }

    /**
     * Synchronize all companies from Teamleader API to local database
     */
    @Async
    public CompletableFuture<Map<String, Object>> syncAllCompanies() {
        logger.info("Starting companies synchronization");
        Map<String, Object> summary = new HashMap<>();
        int[] stats = { 0, 0, 0, 0 }; // total, created, updated, errors

        try {
            fetchAndProcessCompanies(stats);
            userSyncService.updateExistingUserRoles();

            summary.put("success", true);
            summary.put("totalCompanies", stats[0]);
            summary.put("created", stats[1]);
            summary.put("updated", stats[2]);
            summary.put("errors", stats[3]);
            summary.put("timestamp", LocalDateTime.now().toString());

            logger.info("Sync completed: {} total, {} created, {} updated, {} errors",
                    stats[0], stats[1], stats[2], stats[3]);
        } catch (Exception e) {
            logger.error("Sync error", e);
            summary.put("success", false);
            summary.put("error", e.getMessage());
            summary.put("timestamp", LocalDateTime.now().toString());
        }

        return CompletableFuture.completedFuture(summary);
    }

    private void fetchAndProcessCompanies(int[] stats) {
        int page = 1;
        int pageSize = 50;
        boolean hasMorePages = true;

        while (hasMorePages) {
            JsonNode companiesResponse = companyService.getCompanies(page, pageSize);

            if (companiesResponse == null || companiesResponse.has("error") ||
                    !companiesResponse.has("data") || !companiesResponse.get("data").isArray()) {
                stats[3]++; // errors
                break;
            }

            JsonNode companiesData = companiesResponse.get("data");
            int count = companiesData.size();

            for (JsonNode companyNode : companiesData) {
                try {
                    JsonNode details = companyService.getCompanyDetails(companyNode.get("id").asText());
                    if (details == null || details.has("error") || !details.has("data")) {
                        stats[3]++; // errors
                        continue;
                    }

                    boolean isNew = processCompany(details.get("data"));
                    if (isNew)
                        stats[1]++;
                    else
                        stats[2]++; // created or updated
                    stats[0]++; // total
                } catch (Exception e) {
                    logger.error("Error processing company", e);
                    stats[3]++; // errors
                }
            }

            hasMorePages = count == pageSize;
            page++;
        }
    }

    /**
     * Process a company from Teamleader and save to database if it has access
     */
    private boolean processCompany(JsonNode data) {
        String id = data.get("id").asText();
        String name = data.get("name").asText();

        // Check if company exists
        Optional<TeamleaderCompany> existing = companyRepository.findByTeamleaderId(id);
        boolean isNew = !existing.isPresent();

        // Check for MyCLOUDMEN access
        boolean hasAccess = checkAccess(data);

        // If no access, remove if exists and return
        if (!hasAccess) {
            if (!isNew) {
                companyRepository.delete(existing.get());
                logger.info("Removed company without access: {}", name);
            }
            return false;
        }

        // Create or update company
        TeamleaderCompany company = isNew ? new TeamleaderCompany() : existing.get();
        company.setTeamleaderId(id);
        company.setName(name);

        // Set company fields
        if (data.has("website"))
            company.setWebsite(data.get("website").asText());
        if (data.has("vat_number"))
            company.setVatNumber(data.get("vat_number").asText());

        // Set custom fields
        Map<String, Object> customFields = extractCustomFields(data);
        if (!customFields.isEmpty())
            company.setCustomFields(customFields);

        // Set contact info
        List<TeamleaderCompany.ContactInfo> contacts = extractContactInfo(data);
        if (!contacts.isEmpty())
            company.setContactInfo(contacts);

        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        if (isNew)
            company.setCreatedAt(now);
        company.setUpdatedAt(now);
        company.setSyncedAt(now);

        // Save
        companyRepository.save(company);
        logger.info("{} company: {}", isNew ? "Created" : "Updated", name);

        return isNew;
    }

    /**
     * Check if company has MyCLOUDMEN access
     */
    private boolean checkAccess(JsonNode data) {
        if (!data.has("custom_fields"))
            return true; // Default to true if no fields

        String accessFieldId = teamleaderConfig.getMyCloudmenAccessFieldId();
        JsonNode fields = data.get("custom_fields");

        // Check array format
        if (fields.isArray()) {
            for (JsonNode field : fields) {
                if (field.has("definition") && field.has("value") &&
                        field.get("definition").has("id") &&
                        field.get("definition").get("id").asText().equals(accessFieldId)) {
                    return parseAccessValue(field.get("value"));
                }
            }
        }
        // Check object format
        else if (fields.isObject() && fields.has(accessFieldId)) {
            return parseAccessValue(fields.get(accessFieldId));
        }

        return true; // Default to true if field not found
    }

    /**
     * Parse access value from different formats
     */
    private boolean parseAccessValue(JsonNode value) {
        if (value.isTextual()) {
            String text = value.asText().toLowerCase();
            return text.contains("true") || text.contains("yes") ||
                    text.contains("1") || text.contains("on") || text.contains("enable");
        } else if (value.isBoolean())
            return value.asBoolean();
        else if (value.isNumber())
            return value.asInt() > 0;
        else if (value.isObject() && value.has("value"))
            return parseAccessValue(value.get("value"));
        return false;
    }

    /**
     * Extract custom fields from company data
     */
    private Map<String, Object> extractCustomFields(JsonNode data) {
        Map<String, Object> fields = new HashMap<>();
        if (!data.has("custom_fields"))
            return fields;

        JsonNode customFields = data.get("custom_fields");

        // Array format
        if (customFields.isArray()) {
            for (JsonNode field : customFields) {
                if (field.has("definition") && field.has("value") && field.get("definition").has("id")) {
                    String id = field.get("definition").get("id").asText();
                    fields.put(id, extractValue(field.get("value")));
                }
            }
        }
        // Object format
        else if (customFields.isObject()) {
            customFields.fields().forEachRemaining(entry -> fields.put(entry.getKey(), extractValue(entry.getValue())));
        }

        // Add status if available
        if (data.has("status"))
            fields.put("status", data.get("status").asText());

        return fields;
    }

    /**
     * Extract contact info from company data
     */
    private List<TeamleaderCompany.ContactInfo> extractContactInfo(JsonNode data) {
        List<TeamleaderCompany.ContactInfo> contacts = new ArrayList<>();

        // Process emails
        if (data.has("emails") && data.get("emails").isArray()) {
            for (JsonNode email : data.get("emails")) {
                if (email.has("type") && email.has("email")) {
                    contacts.add(new TeamleaderCompany.ContactInfo(
                            "email-" + email.get("type").asText(),
                            email.get("email").asText()));
                }
            }
        }

        // Process phones
        if (data.has("telephones") && data.get("telephones").isArray()) {
            for (JsonNode phone : data.get("telephones")) {
                if (phone.has("type") && phone.has("number")) {
                    contacts.add(new TeamleaderCompany.ContactInfo(
                            "phone-" + phone.get("type").asText(),
                            phone.get("number").asText()));
                }
            }
        }

        return contacts;
    }

    /**
     * Extract value based on type
     */
    private Object extractValue(JsonNode value) {
        if (value.isTextual())
            return value.asText();
        else if (value.isNumber())
            return value.asDouble();
        else if (value.isBoolean())
            return value.asBoolean();
        else if (value.isNull())
            return null;
        return value.toString();
    }

    /**
     * Refresh custom fields for all companies
     */
    @Async
    public CompletableFuture<Map<String, Object>> refreshCustomFields() {
        logger.info("Starting custom fields refresh");
        Map<String, Object> summary = new HashMap<>();
        int[] stats = { 0, 0, 0, 0 }; // total, updated, removed, errors

        try {
            Iterable<TeamleaderCompany> companies = companyRepository.findAll();

            for (TeamleaderCompany company : companies) {
                stats[0]++; // total

                try {
                    JsonNode details = companyService.getCompanyDetails(company.getTeamleaderId());
                    if (details == null || details.has("error") || !details.has("data")) {
                        stats[3]++; // errors
                        continue;
                    }

                    JsonNode data = details.get("data");
                    boolean hasAccess = checkAccess(data);

                    if (!hasAccess) {
                        companyRepository.delete(company);
                        stats[2]++; // removed
                        continue;
                    }

                    processCompany(data); // Reuse existing method to update
                    stats[1]++; // updated
                } catch (Exception e) {
                    logger.error("Error refreshing company {}: {}", company.getName(), e.getMessage());
                    stats[3]++; // errors
                }
            }

            summary.put("success", true);
            summary.put("totalCompanies", stats[0]);
            summary.put("updated", stats[1]);
            summary.put("removed", stats[2]);
            summary.put("errors", stats[3]);
            summary.put("timestamp", LocalDateTime.now().toString());

            logger.info("Refresh completed: {} total, {} updated, {} removed, {} errors",
                    stats[0], stats[1], stats[2], stats[3]);
        } catch (Exception e) {
            logger.error("Error during refresh", e);
            summary.put("success", false);
            summary.put("error", e.getMessage());
            summary.put("timestamp", LocalDateTime.now().toString());
        }

        return CompletableFuture.completedFuture(summary);
    }
}