package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.services.TeamleaderCompanyService;
import com.cloudmen.backend.services.TeamleaderOAuthService;
import com.cloudmen.backend.services.UserSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;

/**
 * Controller for handling synchronization of data from Teamleader
 */
@RestController
@RequestMapping("/api/teamleader/sync")
public class TeamleaderSyncController {

    private static final Logger logger = LoggerFactory.getLogger(TeamleaderSyncController.class);
    private final TeamleaderOAuthService oAuthService;
    private final TeamleaderCompanyService companyService;
    private final UserSyncService userSyncService;
    private final ObjectMapper objectMapper;

    // Track the status of the last sync
    private Map<String, Object> lastSyncStatus = new HashMap<>();

    @Autowired
    public TeamleaderSyncController(
            TeamleaderOAuthService oAuthService,
            TeamleaderCompanyService companyService,
            UserSyncService userSyncService,
            ObjectMapper objectMapper) {
        this.oAuthService = oAuthService;
        this.companyService = companyService;
        this.userSyncService = userSyncService;
        this.objectMapper = objectMapper;
    }

    /**
     * Test endpoint to check if Teamleader integration is working
     * 
     * @return Status of the Teamleader integration
     */
    @GetMapping("/test")
    public ResponseEntity<JsonNode> testIntegration() {
        logger.info("Testing Teamleader integration");

        ObjectNode response = objectMapper.createObjectNode();

        if (oAuthService.hasValidToken()) {
            response.put("status", "success");
            response.put("message", "Teamleader integration is working correctly");
            response.put("authorized", true);

            // Add links to other endpoints
            ObjectNode links = response.putObject("links");
            links.put("companies", "/api/teamleader/companies");
            links.put("status", "/api/teamleader/oauth/status");
        } else {
            response.put("status", "error");
            response.put("message", "Teamleader integration is not authorized");
            response.put("authorized", false);

            // Add links for authorization
            ObjectNode links = response.putObject("links");
            links.put("authorize", "/api/teamleader/oauth/authorize");
            links.put("status", "/api/teamleader/oauth/status");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Trigger a manual synchronization of companies from Teamleader
     * 
     * @return Status of the synchronization
     */
    @PostMapping("/companies")
    public ResponseEntity<JsonNode> syncCompanies() {
        logger.info("Manual synchronization of companies requested");

        ObjectNode response = objectMapper.createObjectNode();

        if (!oAuthService.hasValidToken()) {
            logger.warn("Teamleader integration is not authorized - cannot sync companies");
            response.put("status", "error");
            response.put("message", "Teamleader integration is not authorized");
            response.put("authorized", false);

            // Add links for authorization
            ObjectNode links = response.putObject("links");
            links.put("authorize", "/api/teamleader/oauth/authorize");
            links.put("status", "/api/teamleader/oauth/status");

            return ResponseEntity.ok(response);
        }

        // Test the API connection before starting sync
        JsonNode testResult = companyService.testApiConnection();
        if (testResult.has("error")) {
            logger.error("API connection test failed before sync: {}",
                    testResult.has("message") ? testResult.get("message").asText() : "Unknown error");
            response.put("status", "error");
            response.put("message", "API connection test failed: " +
                    (testResult.has("message") ? testResult.get("message").asText() : "Unknown error"));
            return ResponseEntity.ok(response);
        }

        logger.info("API connection test successful, starting synchronization");

        // Start the synchronization process asynchronously
        CompletableFuture<Map<String, Object>> syncFuture = companyService.syncAllCompanies();

        // Set up a callback to update the last sync status when complete
        syncFuture.thenAccept(status -> {
            lastSyncStatus = status;
            lastSyncStatus.put("completedAt", LocalDateTime.now().toString());

            boolean success = status.containsKey("success") ? (boolean) status.get("success") : false;
            if (success) {
                logger.info("Synchronization completed successfully: {}", status);
            } else {
                logger.error("Synchronization completed with errors: {}", status);
            }
        });

        response.put("status", "success");
        response.put("message", "Company synchronization started");
        response.put("syncStarted", true);
        response.put("startedAt", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Get the status of the last synchronization
     * 
     * @return Status of the last synchronization
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        logger.info("Sync status requested");

        Map<String, Object> status = new HashMap<>(lastSyncStatus);

        if (status.isEmpty()) {
            status.put("message", "No synchronization has been performed yet");
            status.put("hasRun", false);
        } else {
            status.put("hasRun", true);
        }

        return ResponseEntity.ok(status);
    }

    /**
     * Webhook endpoint for Teamleader events
     * This allows Teamleader to notify your application when data changes
     * 
     * @param event The event data from Teamleader
     * @return Acknowledgment of the webhook
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> webhook(@RequestBody JsonNode event) {
        logger.info("Received webhook from Teamleader: {}", event);

        Map<String, String> response = new HashMap<>();
        response.put("status", "received");

        // Process the webhook event
        if (event.has("type")) {
            String eventType = event.get("type").asText();

            // Handle different event types
            switch (eventType) {
                case "company.created":
                case "company.updated":
                    handleCompanyEvent(event);
                    break;
                default:
                    logger.info("Unhandled event type: {}", eventType);
                    break;
            }
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Handle company-related events from Teamleader
     * 
     * @param event The event data
     */
    private void handleCompanyEvent(JsonNode event) {
        if (event.has("subject") && event.get("subject").has("id")) {
            String companyId = event.get("subject").get("id").asText();
            logger.info("Processing company event for ID: {}", companyId);

            // Fetch the latest company data and update our database
            JsonNode companyDetails = companyService.getCompanyDetails(companyId);

            if (companyDetails != null && !companyDetails.has("error") && companyDetails.has("data")) {
                // Update the company in our database
                // This is handled by the companyService
                logger.info("Successfully processed company event for ID: {}", companyId);
            } else {
                logger.error("Failed to process company event for ID: {}", companyId);
            }
        }
    }

    /**
     * Refresh custom fields and update user roles
     * 
     * @return Response with status of the refresh
     */
    @PostMapping("/refresh-custom-fields-and-roles")
    public ResponseEntity<Map<String, Object>> refreshCustomFieldsAndRoles() {
        logger.info("Received request to refresh custom fields and update user roles");

        try {
            // First refresh custom fields
            CompletableFuture<Map<String, Object>> customFieldsFuture = companyService.refreshCustomFields();

            // When that's done, update user roles
            customFieldsFuture.thenAccept(result -> {
                logger.info("Custom fields refresh completed, updating user roles");
                try {
                    userSyncService.updateExistingUserRoles();
                    userSyncService.removeRolesFromIneligibleUsers();
                    logger.info("User roles update completed");
                } catch (Exception e) {
                    logger.error("Error updating user roles after custom fields refresh", e);
                }
            });

            // Return immediate acknowledgment
            Map<String, Object> response = new HashMap<>();
            response.put("status", "processing");
            response.put("message", "Custom fields refresh and user roles update started");
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            logger.error("Error starting custom fields and roles refresh", e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to start refresh: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Refresh custom fields for all companies
     * 
     * @return Response with status of the refresh
     */
    @PostMapping("/refresh-custom-fields")
    public ResponseEntity<Map<String, Object>> refreshCustomFields() {
        logger.info("Received request to refresh custom fields");

        try {
            CompletableFuture<Map<String, Object>> future = companyService.refreshCustomFields();

            // Return immediate acknowledgment
            Map<String, Object> response = new HashMap<>();
            response.put("status", "processing");
            response.put("message", "Custom fields refresh started");
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            logger.error("Error starting custom fields refresh", e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to start custom fields refresh: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}