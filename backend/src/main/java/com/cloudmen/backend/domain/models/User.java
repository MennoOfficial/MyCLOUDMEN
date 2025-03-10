package com.cloudmen.backend.domain.models;

import com.cloudmen.backend.domain.enums.RoleType;
import com.cloudmen.backend.domain.enums.StatusType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "users")
public class User {
    @Id
    private String id; // MongoDB will automatically generate this if null

    @Indexed(unique = true)
    private String email;

    private RoleType role;
    private StatusType status;

    @Indexed
    private String primaryDomain;

    private LocalDateTime dateTimeAdded;
    private LocalDateTime dateTimeChanged;

    @Indexed
    private String customerGoogleId;

    // Constructors
    public User() {
        // Default constructor required by MongoDB
    }

    // Constructor with required fields
    public User(String email, RoleType role, StatusType status, String primaryDomain) {
        this.email = email;
        this.role = role;
        this.status = status;
        this.primaryDomain = primaryDomain;
        this.dateTimeAdded = LocalDateTime.now();
    }

    // Pre-persist method to set creation date if not set
    public void prePersist() {
        if (this.dateTimeAdded == null) {
            this.dateTimeAdded = LocalDateTime.now();
        }
        this.dateTimeChanged = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public RoleType getRole() {
        return role;
    }

    public StatusType getStatus() {
        return status;
    }

    public String getPrimaryDomain() {
        return primaryDomain;
    }

    public LocalDateTime getDateTimeAdded() {
        return dateTimeAdded;
    }

    public LocalDateTime getDateTimeChanged() {
        return dateTimeChanged;
    }

    public String getCustomerGoogleId() {
        return customerGoogleId;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setRole(RoleType role) {
        this.role = role;
    }

    public void setStatus(StatusType status) {
        this.status = status;
    }

    public void setPrimaryDomain(String primaryDomain) {
        this.primaryDomain = primaryDomain;
    }

    public void setDateTimeAdded(LocalDateTime dateTimeAdded) {
        this.dateTimeAdded = dateTimeAdded;
    }

    public void setDateTimeChanged(LocalDateTime dateTimeChanged) {
        this.dateTimeChanged = dateTimeChanged;
    }
}