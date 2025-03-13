package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.api.dtos.UserDTO;
import com.cloudmen.backend.api.dtos.UserResponseDTO;
import com.cloudmen.backend.domain.enums.RoleType;
import com.cloudmen.backend.domain.enums.StatusType;
import com.cloudmen.backend.domain.models.User;
import com.cloudmen.backend.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing users in the MyCLOUDMEN system.
 * Provides endpoints for creating, reading, updating, and deleting users,
 * as well as specialized endpoints for Auth0 integration.
 */
@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*") // Allows requests from any origin
public class UserController {
    private final UserService userService;

    /**
     * Constructor with dependency injection for UserService
     * 
     * @param userService The service for user operations
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Get all users in the system
     * 
     * @return ResponseEntity containing a list of all users
     */
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * Get a user by their Auth0 ID
     * This endpoint is used during authentication to retrieve user details
     * 
     * @param auth0Id The Auth0 ID of the user
     * @return ResponseEntity containing the user if found, 404 Not Found otherwise
     */
    @GetMapping("/{auth0Id}")
    public ResponseEntity<User> getUserByAuth0Id(@PathVariable String auth0Id) {
        return userService.getUserByAuth0Id(auth0Id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Check if a user with the given Auth0 ID exists
     * Used during authentication to determine if a new user needs to be created
     * 
     * @param auth0Id The Auth0 ID to check
     * @return ResponseEntity containing true if the user exists, false otherwise
     */
    @GetMapping("/exists/{auth0Id}")
    public ResponseEntity<Boolean> checkUserExists(@PathVariable String auth0Id) {
        boolean exists = userService.getUserByAuth0Id(auth0Id).isPresent();
        return ResponseEntity.ok(exists);
    }

    /**
     * Get the role of a user by their Auth0 ID
     * Used for authorization and UI customization based on user role
     * 
     * @param auth0Id The Auth0 ID of the user
     * @return ResponseEntity containing the user's role, 404 Not Found if user
     *         doesn't exist
     */
    @GetMapping("/{auth0Id}/role")
    public ResponseEntity<Map<String, String>> getUserRole(@PathVariable String auth0Id) {
        return userService.getUserByAuth0Id(auth0Id)
                .map(user -> {
                    // Return the first role or COMPANY_USER as default
                    final RoleType userRole = user.getRoles() != null && !user.getRoles().isEmpty()
                            ? user.getRoles().get(0)
                            : RoleType.COMPANY_USER;

                    // Create a Map instead of anonymous object for better type safety
                    Map<String, String> response = new HashMap<>();
                    response.put("role", userRole.name());

                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Register a new user in the system
     * This endpoint is called when a new user logs in via Auth0 for the first time
     * 
     * @param userDTO Data Transfer Object containing the user information from
     *                Auth0
     * @return ResponseEntity containing the created user with 201 Created status
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> registerUser(@RequestBody UserDTO userDTO) {
        // Extract domain from email for company association
        String email = userDTO.getEmail();
        String domain = email.substring(email.indexOf('@') + 1);

        // Create user with default COMPANY_USER role
        User newUser = new User();
        newUser.setAuth0Id(userDTO.getAuth0Id()); // Store Auth0 ID
        newUser.setEmail(userDTO.getEmail());
        newUser.setName(userDTO.getName());
        newUser.setFirstName(userDTO.getFirstName());
        newUser.setLastName(userDTO.getLastName());
        newUser.setPicture(userDTO.getPicture());
        newUser.setRoles(Arrays.asList(RoleType.COMPANY_USER));
        newUser.setStatus(StatusType.ACTIVATED);
        newUser.setPrimaryDomain(domain);
        newUser.setDateTimeAdded(LocalDateTime.now());

        // Handle Google integration if applicable
        if (userDTO.getProvider() != null && userDTO.getProvider().equals("Google")
                && userDTO.getCustomerGoogleId() != null) {
            newUser.setCustomerGoogleId(userDTO.getCustomerGoogleId());
        }

        User savedUser = userService.createUser(newUser);

        // Create a response DTO with limited information for security
        UserResponseDTO response = new UserResponseDTO(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRoles(),
                savedUser.getAuth0Id());

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Update an existing user
     * 
     * @param id   The MongoDB ID of the user to update
     * @param user The updated user information
     * @return ResponseEntity containing the updated user if found, 404 Not Found
     *         otherwise
     */
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable String id, @RequestBody User user) {
        return userService.updateUser(id, user)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a user from the system
     * 
     * @param id The MongoDB ID of the user to delete
     * @return ResponseEntity with 204 No Content status if successful
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}