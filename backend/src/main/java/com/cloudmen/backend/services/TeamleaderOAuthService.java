package com.cloudmen.backend.services;

import com.cloudmen.backend.config.TeamleaderApiConfig;
import com.cloudmen.backend.domain.models.OAuthToken;
import com.cloudmen.backend.repositories.OAuthTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for handling OAuth2 authentication with Teamleader API.
 * Manages token acquisition, storage, and refreshing.
 */
@Service
public class TeamleaderOAuthService {

    private static final Logger logger = LoggerFactory.getLogger(TeamleaderOAuthService.class);
    private static final String PROVIDER_NAME = "teamleader";

    private final TeamleaderApiConfig config;
    private final RestTemplate restTemplate;
    private final OAuthTokenRepository tokenRepository;
    private final ObjectMapper objectMapper;

    // Lock to prevent multiple token refresh operations at the same time
    private final ReentrantLock tokenRefreshLock = new ReentrantLock();

    public TeamleaderOAuthService(
            TeamleaderApiConfig config,
            RestTemplate restTemplate,
            OAuthTokenRepository tokenRepository,
            ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.tokenRepository = tokenRepository;
        this.objectMapper = objectMapper;
        logger.info("TeamleaderOAuthService initialized with baseUrl: {}", config.getBaseUrl());
    }

    /**
     * Get the authorization URL for the initial OAuth flow
     * 
     * @return URL to redirect the user to for authorization
     */
    public String getAuthorizationUrl() {
        String state = UUID.randomUUID().toString();

        return UriComponentsBuilder.fromHttpUrl(config.getAuthUrl())
                .queryParam("client_id", config.getClientId())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", config.getRedirectUri())
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    /**
     * Exchange an authorization code for an access token and refresh token
     * 
     * @param code The authorization code received from Teamleader
     * @return true if successful, false otherwise
     */
    public boolean exchangeAuthorizationCode(String code) {
        tokenRefreshLock.lock();
        try {
            logger.info("Exchanging authorization code for access token");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", config.getClientId());
            body.add("client_secret", config.getClientSecret());
            body.add("code", code);
            body.add("grant_type", "authorization_code");
            body.add("redirect_uri", config.getRedirectUri());

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    config.getTokenUrl(),
                    requestEntity,
                    String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                saveTokenResponse(jsonResponse);
                logger.info("Successfully exchanged authorization code for tokens");
                return true;
            } else {
                logger.error("Failed to exchange authorization code: {}", response.getBody());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error exchanging authorization code", e);
            return false;
        } finally {
            tokenRefreshLock.unlock();
        }
    }

    /**
     * Get a valid access token, refreshing if necessary
     * 
     * @return The access token or null if not available
     */
    public String getAccessToken() {
        Optional<OAuthToken> tokenOpt = tokenRepository.findByProvider(PROVIDER_NAME);

        if (tokenOpt.isEmpty()) {
            logger.warn("No OAuth token found for Teamleader. Authorization required.");
            return null;
        }

        OAuthToken token = tokenOpt.get();

        // Check if token is expired and needs refresh
        if (token.isAccessTokenExpired()) {
            logger.info("Access token expired. Attempting to refresh...");
            if (!refreshAccessToken(token)) {
                return null;
            }
        }

        // Mark token as used
        token.markAsUsed();
        tokenRepository.save(token);

        return token.getAccessToken();
    }

    /**
     * Check if we have a valid token for Teamleader
     * 
     * @return true if we have a valid token, false otherwise
     */
    public boolean hasValidToken() {
        String token = getAccessToken();
        return token != null && !token.isEmpty();
    }

    /**
     * Refresh the access token using the refresh token
     * 
     * @param token The token to refresh
     * @return true if successful, false otherwise
     */
    private boolean refreshAccessToken(OAuthToken token) {
        tokenRefreshLock.lock();
        try {
            if (token.getRefreshToken() == null || token.getRefreshToken().isEmpty()) {
                logger.error("No refresh token available. Re-authorization required.");
                return false;
            }

            logger.info("Refreshing access token using refresh token");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", config.getClientId());
            body.add("client_secret", config.getClientSecret());
            body.add("refresh_token", token.getRefreshToken());
            body.add("grant_type", "refresh_token");

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    config.getTokenUrl(),
                    requestEntity,
                    String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                updateTokenFromResponse(token, jsonResponse);
                tokenRepository.save(token);
                logger.info("Successfully refreshed access token");
                return true;
            } else {
                logger.error("Failed to refresh token: {}", response.getBody());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error refreshing access token", e);
            return false;
        } finally {
            tokenRefreshLock.unlock();
        }
    }

    /**
     * Save a new token from the OAuth response
     */
    private void saveTokenResponse(JsonNode response) {
        try {
            OAuthToken token = tokenRepository.findByProvider(PROVIDER_NAME)
                    .orElse(new OAuthToken(PROVIDER_NAME));

            updateTokenFromResponse(token, response);
            tokenRepository.save(token);
            logger.info("Successfully saved new OAuth token for Teamleader");
        } catch (Exception e) {
            logger.error("Error saving token response", e);
        }
    }

    /**
     * Update token information from an OAuth response
     */
    private void updateTokenFromResponse(OAuthToken token, JsonNode response) {
        String accessToken = response.get("access_token").asText();
        int expiresIn = response.get("expires_in").asInt();
        String tokenType = response.get("token_type").asText();

        // Refresh token might not be included in a refresh response
        String refreshToken = null;
        if (response.has("refresh_token")) {
            refreshToken = response.get("refresh_token").asText();
        }

        String scope = "";
        if (response.has("scope")) {
            scope = response.get("scope").asText();
        }

        token.updateTokenInfo(accessToken, expiresIn, refreshToken, tokenType, scope);
    }

    /**
     * Get information about the current token
     * 
     * @return Information about the token or null if not available
     */
    public OAuthToken getTokenInfo() {
        return tokenRepository.findByProvider(PROVIDER_NAME).orElse(null);
    }

    /**
     * Revoke the current token (logout)
     * 
     * @return true if successful, false otherwise
     */
    public boolean revokeToken() {
        Optional<OAuthToken> tokenOpt = tokenRepository.findByProvider(PROVIDER_NAME);
        if (tokenOpt.isPresent()) {
            tokenRepository.delete(tokenOpt.get());
            logger.info("OAuth token for Teamleader has been revoked");
            return true;
        }
        return false;
    }
}