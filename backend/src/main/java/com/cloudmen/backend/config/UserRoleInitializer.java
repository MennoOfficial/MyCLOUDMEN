package com.cloudmen.backend.config;

import com.cloudmen.backend.services.UserSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Initializer component that updates user roles when the application starts
 */
@Component
public class UserRoleInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(UserRoleInitializer.class);

    private final UserSyncService userSyncService;

    public UserRoleInitializer(UserSyncService userSyncService) {
        this.userSyncService = userSyncService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        logger.info("Initializing user roles on application startup");
        try {
            int updatedCount = userSyncService.updateExistingUserRoles();
            int removedCount = userSyncService.removeRolesFromIneligibleUsers();
            logger.info("User role initialization completed. Updated {} users, removed roles from {} users.",
                    updatedCount, removedCount);
        } catch (Exception e) {
            logger.error("Error during user role initialization", e);
        }
    }
}