package com.cloudmen.backend.domain.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Model class for storing authentication log entries.
 * This logs all authentication attempts, both successful and failed.
 */
@Document(collection = "authentication_logs")
public class AuthenticationLog {
    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String email;

    private String primaryDomain;

    private String googleUniqueId;

    @Indexed
    private LocalDateTime timestamp;

    private String ipAddress;

    private String userAgent;

    private String failureReason;

    private boolean successful;

    // Default constructor
    public AuthenticationLog() {
        this.timestamp = LocalDateTime.now();
    }

    // Constructor for successful login
    public AuthenticationLog(String email, String userId, String primaryDomain, String googleUniqueId, String ipAddress,
            String userAgent) {
        this.email = email;
        this.userId = userId;
        this.primaryDomain = primaryDomain;
        this.googleUniqueId = googleUniqueId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.timestamp = LocalDateTime.now();
        this.successful = true;
    }

    // Constructor for failed login
    public AuthenticationLog(String email, String ipAddress, String userAgent, String failureReason) {
        this.email = email;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.failureReason = failureReason;
        this.timestamp = LocalDateTime.now();
        this.successful = false;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPrimaryDomain() {
        return primaryDomain;
    }

    public void setPrimaryDomain(String primaryDomain) {
        this.primaryDomain = primaryDomain;
    }

    public String getGoogleUniqueId() {
        return googleUniqueId;
    }

    public void setGoogleUniqueId(String googleUniqueId) {
        this.googleUniqueId = googleUniqueId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }
}