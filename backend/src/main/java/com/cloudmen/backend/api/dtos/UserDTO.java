package com.cloudmen.backend.api.dtos;

import com.cloudmen.backend.domain.enums.RoleType;
import com.cloudmen.backend.domain.enums.StatusType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object for user data.
 * Used for sending and receiving user data between client and server.
 */
public class UserDTO {
    private String auth0Id; // Unique identifier from Auth0
    private String email; // User's email address
    private String name; // Full name
    private String firstName;
    private String lastName;
    private String picture; // URL to profile picture
    private String provider; // Authentication provider (e.g., Google, Auth0)
    private List<RoleType> roles = new ArrayList<>(); // List of roles
    private StatusType status; // Current user status
    private String primaryDomain; // Primary domain for company association
    private String customerGoogleId;
    private String phoneNumber;
    private LocalDateTime dateTimeAdded;

    public UserDTO() {
    }

    // Getters and setters
    public String getAuth0Id() {
        return auth0Id;
    }

    public void setAuth0Id(String auth0Id) {
        this.auth0Id = auth0Id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
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

    public String getCustomerGoogleId() {
        return customerGoogleId;
    }

    public void setCustomerGoogleId(String customerGoogleId) {
        this.customerGoogleId = customerGoogleId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public LocalDateTime getDateTimeAdded() {
        return dateTimeAdded;
    }

    public void setDateTimeAdded(LocalDateTime dateTimeAdded) {
        this.dateTimeAdded = dateTimeAdded;
    }
}