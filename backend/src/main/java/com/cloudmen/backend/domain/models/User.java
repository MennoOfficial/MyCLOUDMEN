package com.cloudmen.backend.domain.models;

import com.cloudmen.backend.domain.enums.RoleType;
import com.cloudmen.backend.domain.enums.StatusType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "users")
public class User {
    @Id
    private String id; // MongoDB will automatically generate this

    @Indexed(unique = true)
    private String email;

    @Indexed(unique = true)
    private String auth0Id; // Store the Auth0 user identifier

    private String name;
    private String firstName;
    private String lastName;
    private String picture;

    private List<RoleType> roles;
    private StatusType status;

    @Indexed
    private String primaryDomain;

    private LocalDateTime dateTimeAdded;
    private LocalDateTime dateTimeChanged;

    @Indexed
    private String customerGoogleId; // Store the Google user identifier

    // Constructors
    public User() {
        // Default constructor required by MongoDB
    }

    // Constructor with required fields
    public User(String email, List<RoleType> roles, StatusType status, String primaryDomain) {
        this.email = email;
        this.roles = roles;
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

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAuth0Id() {
        return auth0Id;
    }

    public void setAuth0Id(String auth0Id) {
        this.auth0Id = auth0Id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPicture() {
        return picture;
    }

    public void setPicture(String picture) {
        this.picture = picture;
    }

    public List<RoleType> getRoles() {
        return roles;
    }

    public void setRoles(List<RoleType> roles) {
        this.roles = roles;
    }

    public StatusType getStatus() {
        return status;
    }

    public void setStatus(StatusType status) {
        this.status = status;
    }

    public String getPrimaryDomain() {
        return primaryDomain;
    }

    public void setPrimaryDomain(String primaryDomain) {
        this.primaryDomain = primaryDomain;
    }

    public LocalDateTime getDateTimeAdded() {
        return dateTimeAdded;
    }

    public void setDateTimeAdded(LocalDateTime dateTimeAdded) {
        this.dateTimeAdded = dateTimeAdded;
    }

    public LocalDateTime getDateTimeChanged() {
        return dateTimeChanged;
    }

    public void setDateTimeChanged(LocalDateTime dateTimeChanged) {
        this.dateTimeChanged = dateTimeChanged;
    }

    public String getCustomerGoogleId() {
        return customerGoogleId;
    }

    public void setCustomerGoogleId(String customerGoogleId) {
        this.customerGoogleId = customerGoogleId;
    }
}