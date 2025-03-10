package com.cloudmen.backend.domain.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "companies")
public class Company {
    @Id
    private String id; // MongoDB will automatically generate this if null

    private String name;

    @Indexed(unique = true)
    private String customerGoogleId;

    private String customerTeamLeaderId;

    @Indexed(unique = true)
    private String primaryDomain;

    private List<String> employers = new ArrayList<>(); // References to User IDs

    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;
    private boolean active = true;

    // Constructors
    public Company() {
        // Default constructor required by MongoDB
    }

    // Constructor with required fields
    public Company(String name, String primaryDomain) {
        this.name = name;
        this.primaryDomain = primaryDomain;
        this.dateCreated = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCustomerGoogleId() {
        return customerGoogleId;
    }

    public String getCustomerTeamLeaderId() {
        return customerTeamLeaderId;
    }

    public String getPrimaryDomain() {
        return primaryDomain;
    }

    public List<String> getEmployers() {
        return employers;
    }

    public LocalDateTime getDateCreated() {
        return dateCreated;
    }

    public LocalDateTime getDateUpdated() {
        return dateUpdated;
    }

    public boolean isActive() {
        return active;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPrimaryDomain(String primaryDomain) {
        this.primaryDomain = primaryDomain;
    }

    public void setDateCreated(LocalDateTime dateCreated) {
        this.dateCreated = dateCreated;
    }

    public void setDateUpdated(LocalDateTime dateUpdated) {
        this.dateUpdated = dateUpdated;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}