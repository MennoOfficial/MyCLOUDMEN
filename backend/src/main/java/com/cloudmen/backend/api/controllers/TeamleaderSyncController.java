package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.services.CompanySyncService;
import com.cloudmen.backend.services.TeamleaderCompanyService;
import com.cloudmen.backend.services.TeamleaderOAuthService;
import com.cloudmen.backend.services.UserSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final CompanySyncService companySyncService;
    private final UserSyncService userSyncService;
    private final ObjectMapper objectMapper;

    // Track the status of the last sync
    private Map<String, Object> lastSyncStatus = new HashMap<>();

    public TeamleaderSyncController(
            TeamleaderOAuthService oAuthService,
            TeamleaderCompanyService companyService,
            CompanySyncService companySyncService,
            UserSyncService userSyncService,
            ObjectMapper objectMapper) {
        this.oAuthService = oAuthService;
        this.companyService = companyService;
        this.companySyncService = companySyncService;
        this.userSyncService = userSyncService;
        this.objectMapper = objectMapper;
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

        // Start the synchronization process asynchronously using the new
        // CompanySyncService
        CompletableFuture<Map<String, Object>> syncFuture = companySyncService.syncAllCompanies();

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
     * Refresh custom fields and update user roles
     * 
     * @return Response with status of the refresh
     */
    @PostMapping("/refresh-custom-fields")
    public ResponseEntity<Map<String, Object>> refreshCustomFields() {
        logger.info("Received request to refresh custom fields and update user roles");

        try {
            // First refresh custom fields using the new CompanySyncService
            CompletableFuture<Map<String, Object>> customFieldsFuture = companySyncService.refreshCustomFields();

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
}