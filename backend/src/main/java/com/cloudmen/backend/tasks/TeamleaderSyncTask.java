package com.cloudmen.backend.tasks;

import com.cloudmen.backend.services.TeamleaderCompanyService;
import com.cloudmen.backend.services.TeamleaderOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Scheduled task for automatic synchronization of Teamleader data
 */
@Component
@EnableScheduling
public class TeamleaderSyncTask {

    private static final Logger logger = LoggerFactory.getLogger(TeamleaderSyncTask.class);

    private final TeamleaderOAuthService oAuthService;
    private final TeamleaderCompanyService companyService;

    @Value("${teamleader.sync.enabled:false}")
    private boolean syncEnabled;

    private Map<String, Object> lastSyncStatus = new HashMap<>();

    public TeamleaderSyncTask(
            TeamleaderOAuthService oAuthService,
            TeamleaderCompanyService companyService) {
        this.oAuthService = oAuthService;
        this.companyService = companyService;
    }

    /**
     * Scheduled task to sync companies from Teamleader
     * Runs daily at 2:00 AM by default
     */
    @Scheduled(cron = "${teamleader.sync.cron:0 0 2 * * ?}")
    public void syncCompanies() {
        if (!syncEnabled) {
            logger.info("Teamleader sync is disabled. Skipping scheduled sync.");
            return;
        }

        if (!oAuthService.hasValidToken()) {
            logger.error("No valid access token available for Teamleader API. Skipping scheduled sync.");
            return;
        }

        logger.info("Starting scheduled synchronization of companies from Teamleader");

        CompletableFuture<Map<String, Object>> syncFuture = companyService.syncAllCompanies();

        syncFuture.thenAccept(status -> {
            lastSyncStatus = status;
            lastSyncStatus.put("completedAt", LocalDateTime.now().toString());
            lastSyncStatus.put("scheduledSync", true);
            logger.info("Scheduled synchronization completed with status: {}", status);
        });
    }

    /**
     * Get the status of the last synchronization
     * 
     * @return Status of the last synchronization
     */
    public Map<String, Object> getLastSyncStatus() {
        return lastSyncStatus;
    }
}