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
import java.util.*;
import java.util.stream.Collectors;

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
     */
    public User syncUserWithAuth0(String email, Map<String, Object> auth0Data) {
        logger.info("Syncing user data for: {}", email);

        // First check if user exists
        Optional<User> existingUser = userService.getUserByEmail(email);

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            logger.info("Found existing user: {}, current status: {}, roles: {}",
                    email, user.getStatus(), user.getRoles());

            // Only update profile fields, don't touch roles or status
            return updateExistingUser(user, auth0Data);
        } else {
            logger.info("Creating new user during sync for: {}", email);
            return createNewUser(email, auth0Data);
        }
    }

    private User updateExistingUser(User user, Map<String, Object> auth0Data) {
        logger.info("Updating existing user: {}", user.getEmail());
        boolean updated = false;

        // Update basic profile fields
        updated |= updateUserField(user::getAuth0Id, user::setAuth0Id, auth0Data.get("sub"));
        updated |= updateUserField(user::getName, user::setName, auth0Data.get("name"));
        updated |= updateUserField(user::getPicture, user::setPicture, auth0Data.get("picture"));
        updated |= updateUserField(user::getFirstName, user::setFirstName, auth0Data.get("given_name"));
        updated |= updateUserField(user::getLastName, user::setLastName, auth0Data.get("family_name"));

        // Update domain if not set
        if ((user.getPrimaryDomain() == null || user.getPrimaryDomain().isEmpty()) && auth0Data.containsKey("email")) {
            String domain = extractDomainFromEmail((String) auth0Data.get("email"));
            if (domain != null) {
                user.setPrimaryDomain(domain);
                updated = true;
            }
        }

        // Update Google ID if applicable
        if (user.getCustomerGoogleId() == null && auth0Data.containsKey("sub")) {
            String sub = (String) auth0Data.get("sub");
            if (sub != null && sub.startsWith("google-oauth2|")) {
                user.setCustomerGoogleId(sub.substring("google-oauth2|".length()));
                updated = true;
            }
        }

        // Don't modify roles or status when just updating profile information
        // Only update timestamp if anything has changed
        if (updated) {
            user.setDateTimeChanged(LocalDateTime.now());
            logger.info("Saving updated user data for: {}", user.getEmail());
            return userRepository.save(user);
        }

        logger.info("No changes needed for user: {}", user.getEmail());
        return user;
    }

    private <T> boolean updateUserField(Getter<T> getter, Setter<T> setter, Object newValue) {
        if (newValue == null)
            return false;

        @SuppressWarnings("unchecked")
        T typedValue = (T) newValue;

        T currentValue = getter.get();
        if (currentValue == null || !currentValue.equals(typedValue)) {
            setter.set(typedValue);
            return true;
        }
        return false;
    }

    @FunctionalInterface
    private interface Getter<T> {
        T get();
    }

    @FunctionalInterface
    private interface Setter<T> {
        void set(T value);
    }

    private User createNewUser(String email, Map<String, Object> auth0Data) {
        logger.info("Creating new user for: {}", email);
        User newUser = new User();
        newUser.setEmail(email);

        // Set basic profile data
        if (auth0Data.containsKey("sub"))
            newUser.setAuth0Id((String) auth0Data.get("sub"));
        if (auth0Data.containsKey("name"))
            newUser.setName((String) auth0Data.get("name"));
        if (auth0Data.containsKey("given_name"))
            newUser.setFirstName((String) auth0Data.get("given_name"));
        if (auth0Data.containsKey("family_name"))
            newUser.setLastName((String) auth0Data.get("family_name"));
        if (auth0Data.containsKey("picture"))
            newUser.setPicture((String) auth0Data.get("picture"));

        // If name is not set but we have first and last name, combine them
        if (newUser.getName() == null && newUser.getFirstName() != null && newUser.getLastName() != null) {
            newUser.setName(newUser.getFirstName() + " " + newUser.getLastName());
        }

        // Set domain from email
        String domain = extractDomainFromEmail(email);
        if (domain != null) {
            newUser.setPrimaryDomain(domain);
            logger.info("Set primary domain for user {}: {}", email, domain);
        }

        // Handle Google ID
        String sub = (String) auth0Data.get("sub");
        if (sub != null && sub.startsWith("google-oauth2|")) {
            newUser.setCustomerGoogleId(sub.substring("google-oauth2|".length()));
        }

        // DEFAULT: Set all users to PENDING with no roles
        newUser.setStatus(StatusType.PENDING);
        newUser.setRoles(new ArrayList<>());

        // First check if user is a system admin - only case that gets SYSTEM_ADMIN role
        if (isSystemAdmin(email, domain)) {
            logger.info("New user {} is recognized as a system admin", email);
            newUser.setRoles(List.of(RoleType.SYSTEM_ADMIN));
            newUser.setStatus(StatusType.ACTIVATED);
        }
        // Check if user's email exactly matches a company contact email - only case
        // that gets COMPANY_ADMIN role
        else if (isExactCompanyContactMatch(email)) {
            logger.info("New user {} is an exact match for a company primary contact, setting as COMPANY_ADMIN", email);
            newUser.setRoles(List.of(RoleType.COMPANY_ADMIN));
            newUser.setStatus(StatusType.ACTIVATED);
        }
        // All other users stay PENDING with no roles - they need to be approved by an
        // admin

        newUser.setDateTimeAdded(LocalDateTime.now());

        logger.info("Saving new user: {}, Domain: {}, Roles: {}, Status: {}",
                newUser.getName(), newUser.getPrimaryDomain(), newUser.getRoles(), newUser.getStatus());

        return userRepository.save(newUser);
    }

    /**
     * Check if a user's email exactly matches a company contact email
     */
    private boolean isExactCompanyContactMatch(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }

        List<TeamleaderCompany> companies = teamleaderCompanyService.getAllCompanies();
        if (companies == null || companies.isEmpty()) {
            return false;
        }

        for (TeamleaderCompany company : companies) {
            // Skip the MyCloudmenAccess check since all companies in our DB should have
            // access
            List<String> companyEmails = getCompanyEmails(company);

            if (companyEmails.isEmpty()) {
                continue;
            }

            for (String companyEmail : companyEmails) {
                if (email.equalsIgnoreCase(companyEmail)) {
                    logger.info("Found exact email match between user {} and company contact in {}",
                            email, company.getName());
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Assign appropriate role to user based on email and domain
     */
    public void assignUserRole(User user) {
        // Initialize empty roles list if null
        List<RoleType> roles = user.getRoles() != null ? new ArrayList<>(user.getRoles()) : new ArrayList<>();
        String email = user.getEmail();
        String domain = user.getPrimaryDomain();

        // DEFAULT: Most users should be PENDING with no roles until approved
        if (user.getStatus() == null) {
            user.setStatus(StatusType.PENDING);
        }

        // Only assign roles for specific cases:

        // 1. System admins - automatically assigned and activated
        if (isSystemAdmin(email, domain)) {
            logger.info("Assigning SYSTEM_ADMIN role to user: {}", email);
            if (!roles.contains(RoleType.SYSTEM_ADMIN)) {
                roles.clear(); // Clear other roles
                roles.add(RoleType.SYSTEM_ADMIN);
            }
            user.setStatus(StatusType.ACTIVATED);
        }
        // 2. Exact company contact matches - automatically assigned and activated
        else if (isExactCompanyContactMatch(email)) {
            logger.info("User {} is an exact match for company contact, assigning COMPANY_ADMIN role", email);
            if (!roles.contains(RoleType.COMPANY_ADMIN)) {
                roles.clear(); // Clear other roles
                roles.add(RoleType.COMPANY_ADMIN);
            }
            user.setStatus(StatusType.ACTIVATED);
        }
        // All other users require manual approval

        user.setRoles(roles);
    }

    private boolean isSystemAdmin(String email, String domain) {
        return userRoleConfig.getSystemAdminDomain().equals(domain) ||
                (userRoleConfig.hasAdminEmail() && userRoleConfig.getSystemAdminEmail().equals(email));
    }

    private boolean isCompanyContactEmail(String email) {
        if (email == null || email.isEmpty()) {
            logger.debug("Empty or null email, cannot be a company contact");
            return false;
        }

        logger.debug("Checking if {} is a company contact email", email);
        List<TeamleaderCompany> companies = teamleaderCompanyService.getAllCompanies();

        if (companies == null || companies.isEmpty()) {
            logger.warn("No companies found when checking for company contact email");
            return false;
        }

        for (TeamleaderCompany company : companies) {
            // Skip MyCloudmenAccess check since all companies in our DB should have access
            List<String> companyEmails = getCompanyEmails(company);

            // Check for exact email match first (high priority match)
            if (companyEmails.contains(email)) {
                logger.info("Email {} is an exact match for primary contact in company {}",
                        email, company.getName());
                return true;
            }

            // As a fallback, check domain-based matching
            String userDomain = extractDomainFromEmail(email);
            if (userDomain != null) {
                for (String companyEmail : companyEmails) {
                    if (email.equalsIgnoreCase(companyEmail)) {
                        logger.info("Email {} is an exact case-insensitive match for company contact in {}",
                                email, company.getName());
                        return true;
                    }

                    String companyDomain = extractDomainFromEmail(companyEmail);
                    if (userDomain.equals(companyDomain)) {
                        logger.info("Email {} matches domain with company contact {} from {}",
                                email, companyEmail, company.getName());
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private List<String> getCompanyEmails(TeamleaderCompany company) {
        List<TeamleaderCompany.ContactInfo> contactInfo = company.getContactInfo();

        if (contactInfo == null || contactInfo.isEmpty()) {
            return Collections.emptyList();
        }

        // Get all emails - primary and non-primary
        List<String> allEmails = new ArrayList<>();

        // Check for explicit email field first (if it exists in the model)
        try {
            if (company.getClass().getMethod("getEmail").invoke(company) != null) {
                String mainEmail = (String) company.getClass().getMethod("getEmail").invoke(company);
                if (mainEmail != null && !mainEmail.isEmpty()) {
                    allEmails.add(mainEmail);
                }
            }
        } catch (Exception e) {
            // Expected if getEmail method doesn't exist, ignore
        }

        // Get primary emails first
        List<String> primaryEmails = contactInfo.stream()
                .filter(info -> "email-primary".equals(info.getType()))
                .map(TeamleaderCompany.ContactInfo::getValue)
                .filter(email -> email != null && !email.isEmpty())
                .collect(Collectors.toList());

        allEmails.addAll(primaryEmails);

        // Then check for any contact info that contains "email" in type
        List<String> otherEmails = contactInfo.stream()
                .filter(info -> info.getType() != null
                        && info.getType().toLowerCase().contains("email")
                        && !"email-primary".equals(info.getType()))
                .map(TeamleaderCompany.ContactInfo::getValue)
                .filter(email -> email != null && !email.isEmpty())
                .collect(Collectors.toList());

        allEmails.addAll(otherEmails);

        return allEmails;
    }

    private TeamleaderCompany findCompanyByDomain(String domain) {
        if (domain == null || domain.isEmpty()) {
            logger.debug("Empty or null domain, cannot find matching company");
            return null;
        }

        logger.debug("Looking for company matching domain: {}", domain);
        List<TeamleaderCompany> companies = teamleaderCompanyService.getAllCompanies();

        if (companies == null || companies.isEmpty()) {
            logger.warn("No companies found when looking for domain match");
            return null;
        }

        // First try to find direct domain match via website (as domain isn't stored
        // directly)
        for (TeamleaderCompany company : companies) {
            // Skip the MyCloudmenAccess check since all companies in our DB should have
            // access

            // Check if website matches domain
            String website = company.getWebsite();
            if (website != null) {
                String websiteDomain = extractDomainFromUrl(website);
                if (websiteDomain != null && websiteDomain.equalsIgnoreCase(domain)) {
                    logger.info("Found company {} with website domain match: {}",
                            company.getName(), domain);
                    return company;
                }
            }

            // Check all company email domains
            for (String email : getCompanyEmails(company)) {
                String companyDomain = extractDomainFromEmail(email);
                if (companyDomain != null && companyDomain.equalsIgnoreCase(domain)) {
                    logger.info("Found company {} via email domain match: {}",
                            company.getName(), domain);
                    return company;
                }
            }
        }

        logger.debug("No company found matching domain: {}", domain);
        return null;
    }

    /**
     * Extract domain from a URL
     */
    private String extractDomainFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        // Remove protocol
        String domain = url.toLowerCase();
        if (domain.startsWith("http://")) {
            domain = domain.substring("http://".length());
        } else if (domain.startsWith("https://")) {
            domain = domain.substring("https://".length());
        }

        // Remove www. prefix if present
        if (domain.startsWith("www.")) {
            domain = domain.substring("www.".length());
        }

        // Remove path and query string
        int pathStart = domain.indexOf('/');
        if (pathStart > 0) {
            domain = domain.substring(0, pathStart);
        }

        return domain;
    }

    private String extractDomainFromEmail(String email) {
        if (email == null || email.isEmpty() || !email.contains("@")) {
            return null;
        }

        String[] parts = email.split("@");
        String domain = parts.length > 1 ? parts[1] : null;
        return domain;
    }

    /**
     * Update roles for all existing users based on current rules
     * 
     * @return Number of users updated
     */
    public int updateExistingUserRoles() {
        logger.info("Updating roles for all existing users");

        List<User> updatedUsers = userRepository.findAll().stream()
                .filter(this::updateUserRoleIfNeeded)
                .collect(Collectors.toList());

        logger.info("Updated roles for {} users", updatedUsers.size());
        return updatedUsers.size();
    }

    /**
     * Update user roles only if needed based on new rules
     */
    private boolean updateUserRoleIfNeeded(User user) {
        // Keep track of original roles and status
        List<RoleType> oldRoles = new ArrayList<>(user.getRoles());

        // Store if the user was previously eligible for any role
        boolean wasEligibleForRole = !oldRoles.isEmpty();
        boolean currentlyEligibleForRole = isUserEligibleForAnyRole(user);

        // Only make changes if eligibility status has changed
        if (!wasEligibleForRole && currentlyEligibleForRole) {
            // User wasn't eligible before but now is, assign roles
            logger.info("User {} is now eligible for roles, updating", user.getEmail());
            assignUserRole(user);
            user.setDateTimeChanged(LocalDateTime.now());
            userRepository.save(user);
            return true;
        } else if (wasEligibleForRole && !currentlyEligibleForRole) {
            // User was eligible before but isn't now, remove roles
            logger.info("User {} is no longer eligible for roles, removing roles", user.getEmail());
            user.setRoles(new ArrayList<>());
            user.setDateTimeChanged(LocalDateTime.now());
            userRepository.save(user);
            return true;
        } else if (needsSystemAdminOrCompanyAdmin(user, oldRoles)) {
            // Special case: check if user should be promoted to system admin or company
            // admin
            logger.info("User {} needs role promotion, updating", user.getEmail());
            assignUserRole(user);
            user.setDateTimeChanged(LocalDateTime.now());
            userRepository.save(user);
            return true;
        }

        return false;
    }

    /**
     * Check if user should be promoted to system admin or company admin
     */
    private boolean needsSystemAdminOrCompanyAdmin(User user, List<RoleType> currentRoles) {
        String email = user.getEmail();
        String domain = user.getPrimaryDomain();

        // Check if user should be system admin but isn't
        if (isSystemAdmin(email, domain) && !currentRoles.contains(RoleType.SYSTEM_ADMIN)) {
            return true;
        }

        // Check if user should be company admin but isn't
        if (isCompanyContactEmail(email) && !currentRoles.contains(RoleType.COMPANY_ADMIN)) {
            return true;
        }

        return false;
    }

    /**
     * Remove roles from users who are no longer eligible for any role
     * 
     * @return Number of users updated
     */
    public int removeRolesFromIneligibleUsers() {
        logger.info("Checking for users who should no longer have roles");

        List<User> updatedUsers = userRepository.findAll().stream()
                .filter(user -> !user.getRoles().isEmpty())
                .filter(user -> !isUserEligibleForAnyRole(user))
                .map(this::clearUserRoles)
                .collect(Collectors.toList());

        logger.info("Removed roles from {} users", updatedUsers.size());
        return updatedUsers.size();
    }

    private boolean isUserEligibleForAnyRole(User user) {
        String email = user.getEmail();
        String domain = user.getPrimaryDomain();

        return isSystemAdmin(email, domain)
                || isCompanyContactEmail(email)
                || findCompanyByDomain(domain) != null;
    }

    private User clearUserRoles(User user) {
        logger.info("Removing roles from user {} who is no longer eligible", user.getEmail());
        user.setRoles(new ArrayList<>());
        user.setDateTimeChanged(LocalDateTime.now());
        return userRepository.save(user);
    }
}