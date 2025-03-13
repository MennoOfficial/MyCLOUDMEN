package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.services.AuthenticationLogService;
import com.cloudmen.backend.services.UserSyncService;
import com.cloudmen.backend.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller to handle Auth0 authentication callbacks
 */
@RestController
@RequestMapping({ "/api/auth0", "/auth0" })
public class Auth0Controller {

    private static final Logger logger = LoggerFactory.getLogger(Auth0Controller.class);
    private final AuthenticationLogService authenticationLogService;
    private final UserSyncService userSyncService;

    public Auth0Controller(AuthenticationLogService authenticationLogService, UserSyncService userSyncService) {
        this.authenticationLogService = authenticationLogService;
        this.userSyncService = userSyncService;
    }

    /**
     * Endpoint to log successful Auth0 authentication
     * This can be called from the frontend after successful authentication
     */
    @PostMapping("/log-authentication")
    public ResponseEntity<Void> logAuthentication(
            @RequestBody Map<String, Object> authData,
            HttpServletRequest request) {

        logger.info("Received authentication log request: {}", authData);

        String email = (String) authData.get("email");
        String ipAddress = RequestUtils.getClientIpAddress(request);
        String userAgent = RequestUtils.getUserAgent(request);

        logger.info("Authentication log details - Email: {}, IP: {}", email, ipAddress);

        if (email != null && !email.isEmpty()) {
            try {
                // Sync user data with Auth0
                userSyncService.syncUserWithAuth0(email, authData);
                logger.info("User data synchronized for: {}", email);

                // Log the authentication
                authenticationLogService.logSuccessfulAuthentication(email, ipAddress, userAgent);
                logger.info("Successfully logged authentication for: {}", email);

                return ResponseEntity.ok().build();
            } catch (Exception e) {
                logger.error("Error logging authentication", e);
                return ResponseEntity.internalServerError().build();
            }
        } else {
            logger.warn("Missing email in authentication log request");
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Endpoint to log failed Auth0 authentication
     * This can be called from the frontend after failed authentication
     */
    @PostMapping("/log-authentication-failure")
    public ResponseEntity<Void> logAuthenticationFailure(
            @RequestBody Map<String, String> authData,
            HttpServletRequest request) {

        logger.info("Received authentication failure log request: {}", authData);

        String email = authData.get("email");
        String failureReason = authData.get("reason");
        String ipAddress = RequestUtils.getClientIpAddress(request);
        String userAgent = RequestUtils.getUserAgent(request);

        logger.info("Authentication failure log details - Email: {}, Reason: {}, IP: {}",
                email, failureReason, ipAddress);

        try {
            authenticationLogService.logFailedAuthentication(
                    email, ipAddress, userAgent, failureReason);
            logger.info("Successfully logged authentication failure for: {}", email);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error logging authentication failure", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}