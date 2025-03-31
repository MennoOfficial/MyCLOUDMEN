package com.cloudmen.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the Teamleader API integration.
 * Values are loaded from application.properties or environment variables.
 */
@Configuration
@ConfigurationProperties(prefix = "teamleader.api")
@Component
public class TeamleaderApiConfig {

    @Value("${teamleader.baseUrl:https://api.focus.teamleader.eu}")
    private String baseUrl;

    @Value("${teamleader.authUrl:https://focus.teamleader.eu/oauth2/authorize}")
    private String authUrl;

    @Value("${teamleader.tokenUrl:https://focus.teamleader.eu/oauth2/access_token}")
    private String tokenUrl;

    @Value("${teamleader.clientId}")
    private String clientId;

    @Value("${teamleader.clientSecret}")
    private String clientSecret;

    @Value("${teamleader.redirectUri}")
    private String redirectUri;

    /**
     * Synchronization interval in milliseconds (default: 1 hour)
     */
    private long syncIntervalMs = 3600000;

    /**
     * Maximum number of companies to fetch per page
     */
    private int pageSize = 20;

    /**
     * Whether to enable automatic synchronization
     */
    private boolean autoSyncEnabled = true;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAuthUrl() {
        return authUrl;
    }

    public void setAuthUrl(String authUrl) {
        this.authUrl = authUrl;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public long getSyncIntervalMs() {
        return syncIntervalMs;
    }

    public void setSyncIntervalMs(long syncIntervalMs) {
        this.syncIntervalMs = syncIntervalMs;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public boolean isAutoSyncEnabled() {
        return autoSyncEnabled;
    }

    public void setAutoSyncEnabled(boolean autoSyncEnabled) {
        this.autoSyncEnabled = autoSyncEnabled;
    }
}