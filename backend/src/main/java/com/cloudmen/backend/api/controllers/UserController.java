package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.api.dtos.users.UserDTO;
import com.cloudmen.backend.api.dtos.users.UserResponseDTO;
import com.cloudmen.backend.domain.enums.RoleType;
import com.cloudmen.backend.domain.enums.StatusType;
import com.cloudmen.backend.domain.models.User;
import com.cloudmen.backend.services.UserEmailService;
import com.cloudmen.backend.services.UserService;
import com.cloudmen.backend.services.UserSyncService;
import com.cloudmen.backend.services.AuthenticationLogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final UserEmailService userEmailService;
    private final AuthenticationLogService authenticationLogService;
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    /**
     * Constructor with dependency injection for UserService and UserSyncService
     * 
     * @param userService              The service for user operations
     * @param userSyncService          The service for user synchronization
     * @param userEmailService         The service for sending user-related emails
     * @param authenticationLogService The service for authentication log operations
     */
    public UserController(UserService userService, UserSyncService userSyncService, UserEmailService userEmailService,
            AuthenticationLogService authenticationLogService) {
        this.userService = userService;
        this.userSyncService = userSyncService;
        this.userEmailService = userEmailService;
        this.authenticationLogService = authenticationLogService;
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
     * Get users with their last login times - optimized for frontend display
     * 
     * @param domain        The email domain to filter users by (optional)
     * @param status        The status to filter users by (optional)
     * @param excludeStatus The status to exclude from results (optional)
     * @param role          The role to filter users by (optional)
     * @return ResponseEntity containing a filtered list of users with last login
     *         data
     */
    @GetMapping("/with-last-login")
    public ResponseEntity<List<Map<String, Object>>> getUsersWithLastLogin(
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

        // Enhance users with last login information
        List<Map<String, Object>> usersWithLastLogin = users.stream()
                .map(user -> {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("id", user.getId());
                    userMap.put("email", user.getEmail());
                    userMap.put("name", user.getName());
                    userMap.put("firstName", user.getFirstName());
                    userMap.put("lastName", user.getLastName());
                    userMap.put("picture", user.getPicture());
                    userMap.put("status", user.getStatus());
                    userMap.put("roles", user.getRoles());
                    userMap.put("primaryDomain", user.getPrimaryDomain());
                    userMap.put("dateTimeAdded", user.getDateTimeAdded());
                    userMap.put("dateTimeChanged", user.getDateTimeChanged());

                    // Get last login time - try by user ID first, then by email
                    LocalDateTime lastLogin = null;
                    try {
                        if (user.getId() != null) {
                            lastLogin = authenticationLogService.getLastSuccessfulLoginByUserId(user.getId());
                        }
                        if (lastLogin == null && user.getEmail() != null) {
                            lastLogin = authenticationLogService.getLastSuccessfulLoginByEmail(user.getEmail());
                        }
                    } catch (Exception e) {
                        logger.warn("Error fetching last login for user {}: {}", user.getEmail(), e.getMessage());
                    }

                    userMap.put("lastLogin", lastLogin);

                    return userMap;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(usersWithLastLogin);
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

                        // Send approval email
                        try {
                            userEmailService.sendUserApprovalEmail(updatedUser);
                            logger.info("Approval email sent to user: {}", updatedUser.getEmail());
                        } catch (Exception e) {
                            // Log the error but don't fail the approval process
                            logger.error("Failed to send approval email to {}: {}",
                                    updatedUser.getEmail(), e.getMessage(), e);
                        }

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
     * @return ResponseEntity containing the updated user with REJECTED status
     */
    @PostMapping("/pending/{userId}/reject")
    public ResponseEntity<User> rejectUser(@PathVariable String userId) {
        return userService.getUserById(userId)
                .map(user -> {
                    if (user.getStatus() == StatusType.PENDING) {
                        user.setStatus(StatusType.REJECTED);
                        user.setDateTimeChanged(LocalDateTime.now());

                        User updatedUser = userService.updateUser(user.getId(), user)
                                .orElse(user); // Fall back to original user if update fails

                        logger.info("User {} has been rejected and marked with REJECTED status", user.getEmail());
                        return ResponseEntity.ok(updatedUser);
                    } else {
                        return ResponseEntity.badRequest()
                                .body(user); // User is not in PENDING status
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
     * This endpoint is called when a new user logs in via Auth0 for the first time.
     * By default, users are created with PENDING status and no roles.
     * Only system admins and company primary contacts are auto-activated.
     * 
     * @param userDTO Data Transfer Object containing the user information from
     *                Auth0
     * @return ResponseEntity containing the created user with 201 Created status
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody UserDTO userDTO) {
        logger.info("Attempting to register user with Auth0 ID: {}, Email: {}", userDTO.getAuth0Id(),
                userDTO.getEmail());

        // Validate required fields
        if (userDTO.getAuth0Id() == null || userDTO.getAuth0Id().trim().isEmpty()) {
            logger.error("Missing Auth0 ID in registration request");
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", true);
            errorResponse.put("message", "Auth0 ID is required");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        if (userDTO.getEmail() == null || userDTO.getEmail().trim().isEmpty()) {
            logger.error("Missing email in registration request");
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", true);
            errorResponse.put("message", "Email is required");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Check if user already exists by Auth0 ID
        Optional<User> existingUserByAuth0Id = userService.getUserByAuth0Id(userDTO.getAuth0Id());
        if (existingUserByAuth0Id.isPresent()) {
            logger.info("User with Auth0 ID {} already exists, returning existing user", userDTO.getAuth0Id());
            User existingUser = existingUserByAuth0Id.get();

            // Create a response DTO with limited information for security
            UserResponseDTO response = new UserResponseDTO(
                    existingUser.getId(),
                    existingUser.getEmail(),
                    existingUser.getRoles(),
                    existingUser.getAuth0Id());

            return ResponseEntity.ok(response); // Return 200 OK with existing user
        }

        // Check if user already exists by email
        Optional<User> existingUserByEmail = userService.getUserByEmail(userDTO.getEmail());
        if (existingUserByEmail.isPresent()) {
            logger.info("User with email {} already exists, updating Auth0 ID if needed", userDTO.getEmail());
            User existingUser = existingUserByEmail.get();

            // Update the Auth0 ID if it's different (user might have logged in with
            // different provider)
            if (!userDTO.getAuth0Id().equals(existingUser.getAuth0Id())) {
                existingUser.setAuth0Id(userDTO.getAuth0Id());
                existingUser.setDateTimeChanged(LocalDateTime.now());
                userService.updateUser(existingUser.getId(), existingUser);
                logger.info("Updated Auth0 ID for existing user {}", userDTO.getEmail());
            }

            // Create a response DTO with limited information for security
            UserResponseDTO response = new UserResponseDTO(
                    existingUser.getId(),
                    existingUser.getEmail(),
                    existingUser.getRoles(),
                    existingUser.getAuth0Id());

            return ResponseEntity.ok(response); // Return 200 OK with existing user
        }

        // Extract domain from email for company association
        String email = userDTO.getEmail();
        String domain = "";

        // Extract just the domain part after the @ symbol (e.g., "gmail.com" from
        // "user@gmail.com")
        if (email != null && email.contains("@")) {
            domain = email.substring(email.indexOf('@') + 1);
            logger.info("Extracted domain: {} from email: {}", domain, email);
        }

        try {
            // Create user - with default PENDING status
            User newUser = new User();
            newUser.setAuth0Id(userDTO.getAuth0Id());
            newUser.setEmail(userDTO.getEmail());
            newUser.setName(userDTO.getName());
            newUser.setFirstName(userDTO.getFirstName());
            newUser.setLastName(userDTO.getLastName());
            newUser.setPicture(userDTO.getPicture());
            newUser.setStatus(StatusType.PENDING); // Default status, may be changed by UserSyncService
            newUser.setPrimaryDomain(domain);
            newUser.setDateTimeAdded(LocalDateTime.now());
            newUser.setRoles(new ArrayList<>()); // Default to no roles

            // Handle Google integration if applicable
            if (userDTO.getProvider() != null && userDTO.getProvider().equals("Google")
                    && userDTO.getCustomerGoogleId() != null) {
                newUser.setCustomerGoogleId(userDTO.getCustomerGoogleId());
                logger.info("Set Google ID for user: {}", userDTO.getCustomerGoogleId());
            }

            // Let UserSyncService determine if this user qualifies for special role
            // assignment
            logger.info("Checking role eligibility for user: {}", userDTO.getAuth0Id());
            userSyncService.assignUserRole(newUser);
            logger.info("Role assignment complete for user: {}. Roles: {}, Status: {}",
                    userDTO.getAuth0Id(),
                    newUser.getRoles() != null && !newUser.getRoles().isEmpty() ? newUser.getRoles() : "none",
                    newUser.getStatus());

            // Log special situations for debugging
            if (newUser.getStatus() == StatusType.ACTIVATED) {
                if (newUser.getRoles() != null && newUser.getRoles().contains(RoleType.SYSTEM_ADMIN)) {
                    logger.info("User {} was automatically activated as SYSTEM_ADMIN", email);
                } else if (newUser.getRoles() != null && newUser.getRoles().contains(RoleType.COMPANY_ADMIN)) {
                    logger.info("User {} was automatically activated as COMPANY_ADMIN", email);
                }
            } else {
                logger.info("User {} was created with PENDING status and requires admin approval", email);
            }

            // Saves the user via UserService
            logger.info("Creating user in database for Auth0 ID: {}", userDTO.getAuth0Id());
            User savedUser = userService.createUser(newUser);
            logger.info("User successfully created with ID: {} for Auth0 ID: {}", savedUser.getId(),
                    userDTO.getAuth0Id());

            // Create a response DTO with limited information for security
            UserResponseDTO response = new UserResponseDTO(
                    savedUser.getId(),
                    savedUser.getEmail(),
                    savedUser.getRoles(),
                    savedUser.getAuth0Id());

            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            logger.error("Failed to register user with Auth0 ID {}: {}", userDTO.getAuth0Id(), e.getMessage(), e);

            // Create a detailed error response
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", true);
            errorResponse.put("message", "Failed to register user: " + e.getMessage());
            errorResponse.put("auth0Id", userDTO.getAuth0Id());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
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

    /**
     * Update a user's role
     * 
     * @param id          The MongoDB ID of the user to update
     * @param roleRequest The role update request
     * @return ResponseEntity containing the updated user if found, 404 Not Found
     *         otherwise
     */
    @PutMapping("/{id}/role")
    public ResponseEntity<User> updateUserRole(
            @PathVariable String id,
            @RequestBody Map<String, String> roleRequest) {

        String roleName = roleRequest.get("role");
        if (roleName == null || roleName.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            // Convert the role string to RoleType enum
            RoleType newRole = RoleType.valueOf(roleName);

            Optional<User> userOptional = userService.getUserById(id);
            if (userOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            User user = userOptional.get();
            // Set the new role as the first (primary) role
            user.setRoles(Collections.singletonList(newRole));
            user.setDateTimeChanged(LocalDateTime.now());

            Optional<User> updatedUserOptional = userService.updateUser(id, user);
            if (updatedUserOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(updatedUserOptional.get());
        } catch (IllegalArgumentException e) {
            // Invalid role name
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update a user's status
     * 
     * @param id            The MongoDB ID of the user to update
     * @param statusRequest The status update request
     * @return ResponseEntity containing the updated user if found, 404 Not Found
     *         otherwise
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<User> updateUserStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> statusRequest) {

        String statusName = statusRequest.get("status");
        if (statusName == null || statusName.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            // Convert the status string to StatusType enum
            StatusType newStatus = StatusType.valueOf(statusName);

            Optional<User> userOptional = userService.getUserById(id);
            if (userOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            User user = userOptional.get();
            user.setStatus(newStatus);
            user.setDateTimeChanged(LocalDateTime.now());

            Optional<User> updatedUserOptional = userService.updateUser(id, user);
            if (updatedUserOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(updatedUserOptional.get());
        } catch (IllegalArgumentException e) {
            // Invalid status name
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get the current authenticated user based on the Auth0 token
     * 
     * @param request HTTP request containing the Auth0 user info
     * @return ResponseEntity containing the user if found, 404 Not Found otherwise
     */
    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(HttpServletRequest request) {
        // Extract the Auth0 user ID from the request
        String auth0Id = request.getUserPrincipal().getName();

        if (auth0Id == null || auth0Id.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Remove the auth0| prefix if present
        if (auth0Id.startsWith("auth0|")) {
            auth0Id = auth0Id.substring(6);
        }

        logger.info("Getting current user for Auth0 ID: {}", auth0Id);

        return userService.getUserByAuth0Id(auth0Id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get a user by their email address
     * This endpoint is used during status checks to retrieve user details
     * 
     * @param email The email address of the user
     * @return ResponseEntity containing the user if found, 404 Not Found otherwise
     */
    @GetMapping("/email/{email}")
    public ResponseEntity<User> getUserByEmail(@PathVariable String email) {
        return userService.getAllUsers().stream()
                .filter(user -> email.equals(user.getEmail()))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get login times for a specific user
     * 
     * @param userId The ID of the user
     * @return ResponseEntity containing login time information
     */
    @GetMapping("/{userId}/login-times")
    public ResponseEntity<Map<String, Object>> getUserLoginTimes(@PathVariable String userId) {
        return userService.getUserById(userId)
                .map(user -> {
                    Map<String, Object> loginTimes = new HashMap<>();

                    try {
                        // Get first login time
                        LocalDateTime firstLogin = authenticationLogService.getFirstSuccessfulLoginByUserId(userId);
                        if (firstLogin == null && user.getEmail() != null) {
                            firstLogin = authenticationLogService.getFirstSuccessfulLoginByEmail(user.getEmail());
                        }

                        // Get last login time
                        LocalDateTime lastLogin = authenticationLogService.getLastSuccessfulLoginByUserId(userId);
                        if (lastLogin == null && user.getEmail() != null) {
                            lastLogin = authenticationLogService.getLastSuccessfulLoginByEmail(user.getEmail());
                        }

                        loginTimes.put("firstLogin", firstLogin);
                        loginTimes.put("lastLogin", lastLogin);
                        loginTimes.put("hasLoggedIn", firstLogin != null);

                    } catch (Exception e) {
                        logger.warn("Error fetching login times for user {}: {}", user.getEmail(), e.getMessage());
                        loginTimes.put("firstLogin", null);
                        loginTimes.put("lastLogin", null);
                        loginTimes.put("hasLoggedIn", false);
                    }

                    return ResponseEntity.ok(loginTimes);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}