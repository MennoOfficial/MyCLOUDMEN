package com.cloudmen.backend.api.dtos;

import com.cloudmen.backend.domain.enums.RoleType;

import java.util.List;

/**
 * Response DTO for user data.
 * Contains essential user information returned to the client.
 */
public class UserResponseDTO {
    private String id;
    private String email;
    private List<RoleType> roles;
    private String auth0Id;

    public UserResponseDTO() {
    }

    public UserResponseDTO(String id, String email, List<RoleType> roles, String auth0Id) {
        this.id = id;
        this.email = email;
        this.roles = roles;
        this.auth0Id = auth0Id;
    }

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

    public List<RoleType> getRoles() {
        return roles;
    }

    public void setRoles(List<RoleType> roles) {
        this.roles = roles;
    }

    public String getAuth0Id() {
        return auth0Id;
    }

    public void setAuth0Id(String auth0Id) {
        this.auth0Id = auth0Id;
    }
}