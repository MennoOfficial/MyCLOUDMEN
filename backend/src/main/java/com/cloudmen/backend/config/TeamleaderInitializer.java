package com.cloudmen.backend.config;

import com.cloudmen.backend.services.TeamleaderCompanyService;
import com.cloudmen.backend.services.CompanySyncService;
import com.cloudmen.backend.services.TeamleaderOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Initializes Teamleader data synchronization on application startup.
 * This component will trigger a sync of all Teamleader data when the
 * application is ready.
 */
@Component
public class TeamleaderInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(TeamleaderInitializer.class);

    private final TeamleaderOAuthService oAuthService;
    private final TeamleaderCompanyService companyService;
    private final CompanySyncService companySyncService;

    @Value("${teamleader.sync.on-startup:true}")
    private boolean syncOnStartup;

    @Value("${teamleader.sync.startup-delay-ms:5000}")
    private long startupDelayMs;

    public TeamleaderInitializer(
            TeamleaderOAuthService oAuthService,
            TeamleaderCompanyService companyService,
            CompanySyncService companySyncService) {
        this.oAuthService = oAuthService;
        this.companyService = companyService;
        this.companySyncService = companySyncService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!syncOnStartup) {
            logger.info("Teamleader startup synchronization is disabled");
            return;
        }

        // Add a small delay to ensure the application is fully started
        // and to avoid overwhelming the system during startup
        new Thread(() -> {
            try {
                logger.info("Waiting {} ms before starting Teamleader synchronization...", startupDelayMs);
                Thread.sleep(startupDelayMs);
                triggerSync();
            } catch (InterruptedException e) {
                logger.warn("Startup synchronization was interrupted", e);
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void triggerSync() {
        if (!oAuthService.hasValidToken()) {
            logger.warn("Cannot perform startup synchronization: No valid Teamleader OAuth token available");
            return;
        }

        logger.info("Starting Teamleader data synchronization on application startup");

        CompletableFuture<Map<String, Object>> syncFuture = companySyncService.syncAllCompanies();

        syncFuture.thenAccept(result -> {
            if (result.containsKey("success") && (boolean) result.get("success")) {
                int totalCompanies = (int) result.getOrDefault("totalCompanies", 0);
                int created = (int) result.getOrDefault("created", 0);
                int updated = (int) result.getOrDefault("updated", 0);

                logger.info("Startup synchronization completed successfully. " +
                        "Total companies: {}, Created: {}, Updated: {}",
                        totalCompanies, created, updated);
            } else {
                String message = (String) result.getOrDefault("message", "Unknown error");
                logger.error("Startup synchronization failed: {}", message);
            }
        }).exceptionally(ex -> {
            logger.error("Error during startup synchronization", ex);
            return null;
        });
    }
}