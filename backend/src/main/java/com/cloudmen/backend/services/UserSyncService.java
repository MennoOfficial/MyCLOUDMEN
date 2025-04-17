package com.cloudmen.backend.services;

import com.cloudmen.backend.config.UserRoleConfig;
import com.cloudmen.backend.config.TeamleaderConfig;
import com.cloudmen.backend.domain.enums.RoleType;
import com.cloudmen.backend.domain.enums.StatusType;
import com.cloudmen.backend.domain.models.TeamleaderCompany;
import com.cloudmen.backend.domain.models.User;
import com.cloudmen.backend.repositories.UserRepository;
import com.cloudmen.backend.utils.CustomFieldUtils;
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
    private final TeamleaderCompanyService teamleaderCompanyService;
    private final UserRoleConfig userRoleConfig;
    private final TeamleaderConfig teamleaderConfig;

    public UserSyncService(
            UserRepository userRepository,
            UserService userService,
            TeamleaderCompanyService teamleaderCompanyService,
            UserRoleConfig userRoleConfig,
            TeamleaderConfig teamleaderConfig) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.teamleaderCompanyService = teamleaderCompanyService;
        this.userRoleConfig = userRoleConfig;
        this.teamleaderConfig = teamleaderConfig;
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

        // Set Auth0 ID (sub is the unique identifier in Auth0)
        if (auth0Data.containsKey("sub")) {
            newUser.setAuth0Id((String) auth0Data.get("sub"));
        }

        // Set name fields
        if (auth0Data.containsKey("name")) {
            String name = (String) auth0Data.get("name");
            newUser.setName(name);
        }

        // Set first name (given_name in Auth0)
        if (auth0Data.containsKey("given_name")) {
            String firstName = (String) auth0Data.get("given_name");
            newUser.setFirstName(firstName);
        }

        // Set last name (family_name in Auth0)
        if (auth0Data.containsKey("family_name")) {
            String lastName = (String) auth0Data.get("family_name");
            newUser.setLastName(lastName);
        }

        // If name is not set but we have first and last name, combine them
        if (newUser.getName() == null && newUser.getFirstName() != null && newUser.getLastName() != null) {
            String fullName = newUser.getFirstName() + " " + newUser.getLastName();
            newUser.setName(fullName);
        }

        // Set picture URL
        if (auth0Data.containsKey("picture")) {
            String picture = (String) auth0Data.get("picture");
            newUser.setPicture(picture);
        }

        // Extract and set domain from email
        String domain = extractDomainFromEmail(email);
        if (domain != null) {
            newUser.setPrimaryDomain(domain);
        }

        // Handle provider-specific data
        if (auth0Data.containsKey("sub")) {
            String sub = (String) auth0Data.get("sub");
            if (sub.startsWith("google-oauth2|")) {
                String googleId = sub.substring("google-oauth2|".length());
                newUser.setCustomerGoogleId(googleId);
            }
        }

        // Determine user role based on email and domain
        assignUserRole(newUser);

        // Set status to PENDING by default (will be updated to ACTIVATED for certain
        // roles)
        newUser.setStatus(StatusType.PENDING);

        // Set creation date
        newUser.setDateTimeAdded(LocalDateTime.now());

        logger.info("Saving new user with details - Name: {}, Email: {}, Domain: {}, Roles: {}",
                newUser.getName(), newUser.getEmail(), newUser.getPrimaryDomain(), newUser.getRoles());
        return userRepository.save(newUser);
    }

    /**
     * Assign appropriate role to user based on email and domain
     * 
     * @param user User to assign role to
     */
    public void assignUserRole(User user) {
        List<RoleType> roles = new ArrayList<>();
        String email = user.getEmail();
        String domain = user.getPrimaryDomain();

        // Check if user is system admin (based on domain or exact email match if
        // configured)
        if (userRoleConfig.getSystemAdminDomain().equals(domain) ||
                (userRoleConfig.hasAdminEmail() && userRoleConfig.getSystemAdminEmail().equals(email))) {
            logger.info("Assigning SYSTEM_ADMIN role to user: {}", email);
            roles.add(RoleType.SYSTEM_ADMIN);
            user.setStatus(StatusType.ACTIVATED); // System admins are automatically activated
        }
        // Check if user's email exactly matches a company contact email
        else if (isExactCompanyContactEmail(email)) {
            logger.info("Assigning COMPANY_ADMIN role to exact contact match: {}", email);
            roles.add(RoleType.COMPANY_ADMIN);
            user.setStatus(StatusType.ACTIVATED); // Company admins are automatically activated
        }
        // Check if user's domain matches a company contact email domain
        else {
            TeamleaderCompany matchingCompany = findCompanyByDomain(domain);
            if (matchingCompany != null) {
                logger.info("Assigning COMPANY_USER role to domain match: {} for company: {}", email,
                        matchingCompany.getName());
                roles.add(RoleType.COMPANY_USER);
                user.setStatus(StatusType.PENDING); // Company users start as pending
            } else {
                logger.info("User {} has no associated company, no role assigned", email);
                // No role assigned
                user.setStatus(StatusType.PENDING);
            }
        }

        user.setRoles(roles);
    }

    /**
     * Check if a company has MyCLOUDMEN access enabled
     */
    private boolean hasMyCloudmenAccess(TeamleaderCompany company) {
        if (company == null || company.getCustomFields() == null) {
            return false;
        }

        String fieldId = teamleaderConfig.getMyCloudmenAccessFieldId();
        boolean hasAccess = CustomFieldUtils.isCustomFieldTrue(company.getCustomFields(), fieldId);

        return hasAccess;
    }

    /**
     * Check if the email exactly matches a company contact email
     * 
     * @param email Email to check
     * @return true if email exactly matches a company contact and company has
     *         access
     */
    private boolean isExactCompanyContactEmail(String email) {
        List<TeamleaderCompany> companies = teamleaderCompanyService.getAllCompanies();

        for (TeamleaderCompany company : companies) {
            // Skip companies without MyCLOUDMEN access
            if (!hasMyCloudmenAccess(company)) {
                continue;
            }

            List<TeamleaderCompany.ContactInfo> contactInfoList = company.getContactInfo();
            if (contactInfoList != null) {
                for (TeamleaderCompany.ContactInfo contactInfo : contactInfoList) {
                    if ("email-primary".equals(contactInfo.getType()) && email.equals(contactInfo.getValue())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Find a company by matching the domain against contact email domains
     * 
     * @param domain Domain to check
     * @return Matching TeamleaderCompany or null if none found
     */
    private TeamleaderCompany findCompanyByDomain(String domain) {
        if (domain == null || domain.isEmpty()) {
            return null;
        }

        List<TeamleaderCompany> companies = teamleaderCompanyService.getAllCompanies();

        for (TeamleaderCompany company : companies) {
            // Skip companies without MyCLOUDMEN access
            if (!hasMyCloudmenAccess(company)) {
                continue;
            }

            List<TeamleaderCompany.ContactInfo> contactInfoList = company.getContactInfo();
            if (contactInfoList != null) {
                for (TeamleaderCompany.ContactInfo contactInfo : contactInfoList) {
                    if ("email-primary".equals(contactInfo.getType())) {
                        String contactEmail = contactInfo.getValue();
                        String contactDomain = extractDomainFromEmail(contactEmail);
                        if (domain.equals(contactDomain)) {
                            return company;
                        }
                    }
                }
            }
        }

        return null;
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

    /**
     * Update roles for all existing users based on current rules
     * This can be called after configuration changes or during system
     * initialization
     * 
     * @return Number of users updated
     */
    public int updateExistingUserRoles() {
        logger.info("Updating roles for all existing users");
        List<User> allUsers = userRepository.findAll();
        int updatedCount = 0;

        for (User user : allUsers) {
            List<RoleType> oldRoles = new ArrayList<>(user.getRoles());
            assignUserRole(user);

            // Check if roles have changed
            if (!oldRoles.equals(user.getRoles())) {
                logger.info("Updating roles for user {}: {} -> {}",
                        user.getEmail(), oldRoles, user.getRoles());
                user.setDateTimeChanged(LocalDateTime.now());
                userRepository.save(user);
                updatedCount++;
            }
        }

        logger.info("Updated roles for {} users", updatedCount);
        return updatedCount;
    }

    /**
     * Remove roles from users who are no longer eligible for any role
     * This can be called after configuration changes or during system
     * initialization
     * 
     * @return Number of users updated
     */
    public int removeRolesFromIneligibleUsers() {
        logger.info("Checking for users who should no longer have roles");
        List<User> allUsers = userRepository.findAll();
        int updatedCount = 0;

        for (User user : allUsers) {
            // Skip users with no roles
            if (user.getRoles() == null || user.getRoles().isEmpty()) {
                continue;
            }

            String email = user.getEmail();
            String domain = user.getPrimaryDomain();

            // Check if user should have any role
            boolean shouldHaveRole = false;

            // Check if user is system admin
            if (userRoleConfig.getSystemAdminDomain().equals(domain) ||
                    (userRoleConfig.hasAdminEmail() && userRoleConfig.getSystemAdminEmail().equals(email))) {
                shouldHaveRole = true;
            }
            // Check if user is from Teamleader and should be company admin
            else if (isExactCompanyContactEmail(email)) {
                shouldHaveRole = true;
            }
            // Check if domain matches any known company with MyCLOUDMEN access
            else if (findCompanyByDomain(domain) != null) {
                shouldHaveRole = true;
            }

            // If user should not have any role, remove all roles
            if (!shouldHaveRole) {
                logger.info("Removing roles from user {} who is no longer eligible", email);
                user.setRoles(new ArrayList<>());
                user.setDateTimeChanged(LocalDateTime.now());
                userRepository.save(user);
                updatedCount++;
            }
        }

        logger.info("Removed roles from {} users", updatedCount);
        return updatedCount;
    }
}