package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.domain.models.OAuthToken;
import com.cloudmen.backend.services.TeamleaderOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for handling OAuth authorization with Teamleader.
 * This controller is primarily for admin use during initial setup.
 */
@RestController
@RequestMapping("/api/teamleader/oauth")
public class TeamleaderOAuthController {

    private static final Logger logger = LoggerFactory.getLogger(TeamleaderOAuthController.class);
    private final TeamleaderOAuthService oAuthService;

    public TeamleaderOAuthController(TeamleaderOAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    /**
     * Redirect to Teamleader for authorization
     * This is the first step in the OAuth flow and should be accessed by an admin
     * 
     * @return Redirect to Teamleader authorization page
     */
    @GetMapping("/authorize")
    public RedirectView authorize() {
        logger.info("Initiating Teamleader OAuth authorization flow");
        String authUrl = oAuthService.getAuthorizationUrl();
        return new RedirectView(authUrl);
    }

    /**
     * Endpoint for requesting access to Teamleader API
     * This is a convenience endpoint that redirects to the authorize endpoint
     * 
     * @return Redirect to the authorize endpoint
     */
    @GetMapping("/request-access")
    public RedirectView requestAccess() {
        logger.info("Request access to Teamleader API");
        return authorize();
    }

    /**
     * Callback endpoint for Teamleader OAuth
     * This is where Teamleader will redirect after authorization
     * 
     * @param code  The authorization code
     * @param state The state parameter (for security validation)
     * @return Success or error message
     */
    @GetMapping("/callback")
    public ResponseEntity<Map<String, String>> callback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state) {

        logger.info("Received OAuth callback with code: {}", code);
        Map<String, String> response = new HashMap<>();

        boolean success = oAuthService.exchangeAuthorizationCode(code);

        if (success) {
            response.put("status", "success");
            response.put("message",
                    "Authorization successful! The application is now authorized to access Teamleader API. You can close this window and return to the application.");
            logger.info("Successfully exchanged authorization code for tokens");
        } else {
            response.put("status", "error");
            response.put("message",
                    "Failed to exchange authorization code for tokens. Please try again or contact support.");
            logger.error("Failed to exchange authorization code for tokens");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Check the status of the OAuth token
     * 
     * @return Token status information
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> status = new HashMap<>();
        OAuthToken token = oAuthService.getTokenInfo();

        if (token != null) {
            status.put("authorized", true);
            status.put("provider", token.getProvider());
            status.put("lastUpdated", token.getLastUpdated());
            status.put("lastUsed", token.getLastUsed());
            status.put("tokenExpired", token.isAccessTokenExpired());
            status.put("message", "Teamleader API integration is authorized and ready to use.");
        } else {
            status.put("authorized", false);
            status.put("message",
                    "Teamleader API integration is not authorized. Please use the /api/teamleader/oauth/authorize endpoint to authorize.");
        }

        return ResponseEntity.ok(status);
    }

    /**
     * Revoke the current OAuth token (logout)
     * 
     * @return Success or error message
     */
    @PostMapping("/revoke")
    public ResponseEntity<Map<String, String>> revoke() {
        Map<String, String> response = new HashMap<>();

        boolean success = oAuthService.revokeToken();

        if (success) {
            response.put("status", "success");
            response.put("message",
                    "Token successfully revoked. The application is no longer authorized to access Teamleader API.");
            logger.info("Successfully revoked Teamleader OAuth token");
        } else {
            response.put("status", "error");
            response.put("message", "No token found to revoke or error revoking token.");
            logger.warn("No token found to revoke or error revoking token");
        }

        return ResponseEntity.ok(response);
    }
}