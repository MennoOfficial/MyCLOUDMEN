package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.api.dtos.UserDTO;
import com.cloudmen.backend.api.dtos.UserResponseDTO;
import com.cloudmen.backend.domain.enums.RoleType;
import com.cloudmen.backend.domain.enums.StatusType;
import com.cloudmen.backend.domain.models.User;
import com.cloudmen.backend.services.UserService;
import com.cloudmen.backend.services.UserSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final UserSyncService userSyncService;

    /**
     * Constructor with dependency injection for UserService and UserSyncService
     * 
     * @param userService     The service for user operations
     * @param userSyncService The service for user synchronization
     */
    public UserController(UserService userService, UserSyncService userSyncService) {
        this.userService = userService;
        this.userSyncService = userSyncService;
    }

    /**
     * Get all users in the system with optional filtering
     * 
     * @param domain        The email domain to filter users by (optional)
     * @param status        The status to filter users by (optional)
     * @param excludeStatus The status to exclude from results (optional)
     * @param role          The role to filter users by (optional)
     * @return ResponseEntity containing a filtered list of users
     */
    @GetMapping
    public ResponseEntity<List<User>> getUsers(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String excludeStatus,
            @RequestParam(required = false) String role) {

        List<User> users = userService.getAllUsers();

        // Apply domain filter if provided
        if (domain != null && !domain.isEmpty()) {
            users = filterUsersByDomain(users, domain);
        }

        // Apply status filter if provided
        if (status != null && !status.isEmpty()) {
            try {
                StatusType statusType = StatusType.valueOf(status.toUpperCase());
                users = users.stream()
                        .filter(user -> user.getStatus() == statusType)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                // Invalid status provided, return empty list
                return ResponseEntity.ok(new ArrayList<>());
            }
        }

        // Apply exclude status filter if provided
        if (excludeStatus != null && !excludeStatus.isEmpty()) {
            try {
                StatusType excludeStatusType = StatusType.valueOf(excludeStatus.toUpperCase());
                users = users.stream()
                        .filter(user -> user.getStatus() != excludeStatusType)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                // Invalid exclude status, ignore this filter
            }
        }

        // Apply role filter if provided
        if (role != null && !role.isEmpty()) {
            try {
                RoleType roleType = RoleType.valueOf(role.toUpperCase());
                users = users.stream()
                        .filter(user -> user.getRoles() != null &&
                                user.getRoles().contains(roleType))
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                // Invalid role provided, return empty list
                return ResponseEntity.ok(new ArrayList<>());
            }
        }

        return ResponseEntity.ok(users);
    }

    /**
     * Get users by specific domain
     * 
     * @param domain The email domain to filter users by
     * @return ResponseEntity containing a list of users with the specified domain
     */
    @GetMapping("/domain/{domain}")
    public ResponseEntity<List<User>> getUsersByDomain(@PathVariable String domain) {
        List<User> users = filterUsersByDomain(userService.getAllUsers(), domain);
        return ResponseEntity.ok(users);
    }

    /**
     * Get pending users by domain
     * 
     * @param domain The email domain to filter pending users by
     * @return ResponseEntity containing a list of pending users with the specified
     *         domain
     */
    @GetMapping("/pending/domain/{domain}")
    public ResponseEntity<List<User>> getPendingUsersByDomain(@PathVariable String domain) {
        List<User> users = userService.getAllUsers();

        // Filter by PENDING status and domain
        List<User> pendingUsers = users.stream()
                .filter(user -> user.getStatus() == StatusType.PENDING)
                .collect(Collectors.toList());

        pendingUsers = filterUsersByDomain(pendingUsers, domain);

        return ResponseEntity.ok(pendingUsers);
    }

    /**
     * Helper method to filter users by domain
     * 
     * @param users  The list of users to filter
     * @param domain The domain to filter by
     * @return Filtered list of users
     */
    private List<User> filterUsersByDomain(List<User> users, String domain) {
        return users.stream()
                .filter(user -> user.getPrimaryDomain() != null &&
                        user.getPrimaryDomain().equalsIgnoreCase(domain))
                .collect(Collectors.toList());
    }

    /**
     * Admin endpoint to fix users with incorrect primary domain format
     * This will update users that have full emails stored in the primaryDomain
     * field
     * 
     * @return ResponseEntity with information about the update
     */
    @PostMapping("/admin/fix-primary-domains")
    public ResponseEntity<Map<String, Object>> fixPrimaryDomains() {
        List<User> users = userService.getAllUsers();
        int updatedCount = 0;

        for (User user : users) {
            String primaryDomain = user.getPrimaryDomain();

            // Check if primaryDomain contains @ which indicates it might be a full email
            if (primaryDomain != null && primaryDomain.contains("@")) {
                String correctedDomain = primaryDomain.substring(primaryDomain.indexOf('@') + 1);
                user.setPrimaryDomain(correctedDomain);
                user.setDateTimeChanged(LocalDateTime.now());
                userService.updateUser(user.getId(), user);
                updatedCount++;
            }
            // Check if primaryDomain is empty but email is available
            else if ((primaryDomain == null || primaryDomain.isEmpty()) && user.getEmail() != null
                    && user.getEmail().contains("@")) {
                String correctedDomain = user.getEmail().substring(user.getEmail().indexOf('@') + 1);
                user.setPrimaryDomain(correctedDomain);
                user.setDateTimeChanged(LocalDateTime.now());
                userService.updateUser(user.getId(), user);
                updatedCount++;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Primary domains fixed");
        response.put("updatedCount", updatedCount);

        return ResponseEntity.ok(response);
    }

    /**
     * Approve a pending user
     * 
     * @param userId The ID of the user to approve
     * @return ResponseEntity containing the approved user
     */
    @PostMapping("/pending/{userId}/approve")
    public ResponseEntity<User> approveUser(@PathVariable String userId) {
        return userService.getUserById(userId)
                .map(user -> {
                    if (user.getStatus() == StatusType.PENDING) {
                        user.setStatus(StatusType.ACTIVATED);
                        user.setDateTimeChanged(LocalDateTime.now());

                        // Ensure the user has a role
                        if (user.getRoles() == null || user.getRoles().isEmpty()) {
                            user.setRoles(Arrays.asList(RoleType.COMPANY_USER));
                        }

                        User updatedUser = userService.updateUser(user.getId(), user)
                                .orElse(user); // Fall back to original user if update fails
                        return ResponseEntity.ok(updatedUser);
                    } else {
                        return ResponseEntity.badRequest()
                                .body(user); // User is not in PENDING status
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Reject a pending user
     * 
     * @param userId The ID of the user to reject
     * @return ResponseEntity with 204 No Content if successful
     */
    @PostMapping("/pending/{userId}/reject")
    public ResponseEntity<Void> rejectUser(@PathVariable String userId) {
        return userService.getUserById(userId)
                .map(user -> {
                    if (user.getStatus() == StatusType.PENDING) {
                        userService.deleteUser(userId);
                        return ResponseEntity.noContent().<Void>build();
                    } else {
                        return ResponseEntity.badRequest().<Void>build(); // User is not in PENDING status
                    }
                })
                .orElse(ResponseEntity.notFound().build());
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
        String domain = "";

        // Extract just the domain part after the @ symbol (e.g., "gmail.com" from
        // "user@gmail.com")
        if (email != null && email.contains("@")) {
            domain = email.substring(email.indexOf('@') + 1);
            System.out.println("Extracted domain: " + domain + " from email: " + email);
        }

        // Create user
        User newUser = new User();
        newUser.setAuth0Id(userDTO.getAuth0Id());
        newUser.setEmail(userDTO.getEmail());
        newUser.setName(userDTO.getName());
        newUser.setFirstName(userDTO.getFirstName());
        newUser.setLastName(userDTO.getLastName());
        newUser.setPicture(userDTO.getPicture());
        newUser.setStatus(StatusType.PENDING);

        // Set the primaryDomain directly to just the domain part
        newUser.setPrimaryDomain(domain);

        newUser.setDateTimeAdded(LocalDateTime.now());

        // Handle Google integration if applicable
        if (userDTO.getProvider() != null && userDTO.getProvider().equals("Google")
                && userDTO.getCustomerGoogleId() != null) {
            newUser.setCustomerGoogleId(userDTO.getCustomerGoogleId());
        }

        // Let UserSyncService determine the appropriate role
        userSyncService.assignUserRole(newUser);

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