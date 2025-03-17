package com.cloudmen.backend.domain.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Entity for storing OAuth tokens for external API integrations.
 * Used to persist refresh tokens across application restarts.
 */
@Document(collection = "oauth_tokens")
public class OAuthToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String provider; // e.g., "teamleader"

    private String accessToken;
    private LocalDateTime accessTokenExpiresAt;
    private String refreshToken;
    private String tokenType; // Usually "bearer"
    private String scope;

    private LocalDateTime lastUpdated;
    private LocalDateTime lastUsed;

    public OAuthToken() {
        // Default constructor required by MongoDB
    }

    public OAuthToken(String provider) {
        this.provider = provider;
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Checks if the access token is expired or about to expire (within 5 minutes)
     */
    public boolean isAccessTokenExpired() {
        if (accessTokenExpiresAt == null) {
            return true;
        }
        return LocalDateTime.now().plusMinutes(5).isAfter(accessTokenExpiresAt);
    }

    /**
     * Updates the token information after a refresh or initial authorization
     */
    public void updateTokenInfo(String accessToken, int expiresIn, String refreshToken, String tokenType,
            String scope) {
        this.accessToken = accessToken;
        this.accessTokenExpiresAt = LocalDateTime.now().plusSeconds(expiresIn);
        if (refreshToken != null && !refreshToken.isEmpty()) {
            this.refreshToken = refreshToken;
        }
        this.tokenType = tokenType;
        this.scope = scope;
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Records that the token was used
     */
    public void markAsUsed() {
        this.lastUsed = LocalDateTime.now();
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public LocalDateTime getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public void setAccessTokenExpiresAt(LocalDateTime accessTokenExpiresAt) {
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public LocalDateTime getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(LocalDateTime lastUsed) {
        this.lastUsed = lastUsed;
    }
}