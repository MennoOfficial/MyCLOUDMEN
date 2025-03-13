package com.cloudmen.backend.services;

import com.cloudmen.backend.domain.enums.RoleType;
import com.cloudmen.backend.domain.enums.StatusType;
import com.cloudmen.backend.domain.models.User;
import com.cloudmen.backend.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service to synchronize user data between Auth0 and the application database
 */
@Service
public class UserSyncService {
    private static final Logger logger = LoggerFactory.getLogger(UserSyncService.class);

    private final UserRepository userRepository;
    private final UserService userService;

    public UserSyncService(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
        logger.info("UserSyncService initialized");
    }

    /**
     * Synchronize user data from Auth0 with our database
     * 
     * @param email     User's email
     * @param auth0Data Map containing Auth0 user profile data
     * @return Updated User object
     */
    public User syncUserWithAuth0(String email, Map<String, Object> auth0Data) {
        logger.info("Syncing user data for: {}", email);

        // Find existing user by email
        Optional<User> existingUser = userService.getUserByEmail(email);

        if (existingUser.isPresent()) {
            // Update existing user
            return updateExistingUser(existingUser.get(), auth0Data);
        } else {
            // Create new user
            return createNewUser(email, auth0Data);
        }
    }

    /**
     * Update an existing user with Auth0 data
     * 
     * @param user      Existing user
     * @param auth0Data Auth0 profile data
     * @return Updated User object
     */
    private User updateExistingUser(User user, Map<String, Object> auth0Data) {
        logger.info("Updating existing user: {}", user.getEmail());
        boolean updated = false;

        // Update Auth0 ID if not set
        if (user.getAuth0Id() == null && auth0Data.containsKey("sub")) {
            user.setAuth0Id((String) auth0Data.get("sub"));
            updated = true;
        }

        // Update name if available and different
        if (auth0Data.containsKey("name")) {
            String name = (String) auth0Data.get("name");
            if (name != null && !name.equals(user.getName())) {
                user.setName(name);
                updated = true;
            }
        }

        // Update picture if available and different
        if (auth0Data.containsKey("picture")) {
            String picture = (String) auth0Data.get("picture");
            if (picture != null && !picture.equals(user.getPicture())) {
                user.setPicture(picture);
                updated = true;
            }
        }

        // Extract and update first name and last name if available
        if (auth0Data.containsKey("given_name")) {
            String firstName = (String) auth0Data.get("given_name");
            if (firstName != null && !firstName.equals(user.getFirstName())) {
                user.setFirstName(firstName);
                updated = true;
            }
        }

        if (auth0Data.containsKey("family_name")) {
            String lastName = (String) auth0Data.get("family_name");
            if (lastName != null && !lastName.equals(user.getLastName())) {
                user.setLastName(lastName);
                updated = true;
            }
        }

        // Update primary domain if not set
        if ((user.getPrimaryDomain() == null || user.getPrimaryDomain().isEmpty()) &&
                auth0Data.containsKey("email")) {
            String email = (String) auth0Data.get("email");
            String domain = extractDomainFromEmail(email);
            if (domain != null) {
                user.setPrimaryDomain(domain);
                updated = true;
            }
        }

        // Save only if changes were made
        if (updated) {
            user.setDateTimeChanged(LocalDateTime.now());
            logger.info("Saving updated user data for: {}", user.getEmail());
            return userRepository.save(user);
        } else {
            logger.info("No changes needed for user: {}", user.getEmail());
            return user;
        }
    }

    /**
     * Create a new user from Auth0 data
     * 
     * @param email     User's email
     * @param auth0Data Auth0 profile data
     * @return New User object
     */
    private User createNewUser(String email, Map<String, Object> auth0Data) {
        logger.info("Creating new user for: {}", email);

        User newUser = new User();
        newUser.setEmail(email);

        // Set Auth0 ID
        if (auth0Data.containsKey("sub")) {
            newUser.setAuth0Id((String) auth0Data.get("sub"));
        }

        // Set name
        if (auth0Data.containsKey("name")) {
            newUser.setName((String) auth0Data.get("name"));
        }

        // Set picture
        if (auth0Data.containsKey("picture")) {
            newUser.setPicture((String) auth0Data.get("picture"));
        }

        // Set first name
        if (auth0Data.containsKey("given_name")) {
            newUser.setFirstName((String) auth0Data.get("given_name"));
        }

        // Set last name
        if (auth0Data.containsKey("family_name")) {
            newUser.setLastName((String) auth0Data.get("family_name"));
        }

        // Extract and set domain from email
        String domain = extractDomainFromEmail(email);
        if (domain != null) {
            newUser.setPrimaryDomain(domain);
        }

        // Set default role (COMPANY_USER)
        List<RoleType> roles = new ArrayList<>();
        roles.add(RoleType.COMPANY_USER);
        newUser.setRoles(roles);

        // Set status to ACTIVATED
        newUser.setStatus(StatusType.ACTIVATED);

        // Set creation date
        newUser.setDateTimeAdded(LocalDateTime.now());

        logger.info("Saving new user: {}", email);
        return userRepository.save(newUser);
    }

    /**
     * Extract domain from email address
     * 
     * @param email Email address
     * @return Domain part of the email
     */
    private String extractDomainFromEmail(String email) {
        if (email == null || email.isEmpty() || !email.contains("@")) {
            return null;
        }

        String[] parts = email.split("@");
        if (parts.length > 1) {
            return parts[1];
        }

        return null;
    }
}