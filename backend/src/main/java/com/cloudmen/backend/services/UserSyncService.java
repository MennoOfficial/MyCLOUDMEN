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
        return userService.getUserByEmail(email)
                .map(user -> updateExistingUser(user, auth0Data))
                .orElseGet(() -> createNewUser(email, auth0Data));
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

        // Set domain and custom ID
        String domain = extractDomainFromEmail(email);
        if (domain != null) {
            newUser.setPrimaryDomain(domain);
        }

        // Handle Google ID
        String sub = (String) auth0Data.get("sub");
        if (sub != null && sub.startsWith("google-oauth2|")) {
            newUser.setCustomerGoogleId(sub.substring("google-oauth2|".length()));
        }

        // Assign role based on email and domain
        assignUserRole(newUser);

        // Set default status (will be updated to ACTIVATED for certain roles)
        if (newUser.getStatus() == null) {
            newUser.setStatus(StatusType.PENDING);
        }

        newUser.setDateTimeAdded(LocalDateTime.now());

        logger.info("Saving new user: {}, Domain: {}, Roles: {}",
                newUser.getName(), newUser.getPrimaryDomain(), newUser.getRoles());

        return userRepository.save(newUser);
    }

    /**
     * Assign appropriate role to user based on email and domain
     */
    public void assignUserRole(User user) {
        List<RoleType> roles = new ArrayList<>();
        String email = user.getEmail();
        String domain = user.getPrimaryDomain();

        if (isSystemAdmin(email, domain)) {
            logger.info("Assigning SYSTEM_ADMIN role to user: {}", email);
            roles.add(RoleType.SYSTEM_ADMIN);
            user.setStatus(StatusType.ACTIVATED);
        } else if (isCompanyContactEmail(email)) {
            logger.info("Assigning COMPANY_ADMIN role to contact: {}", email);
            roles.add(RoleType.COMPANY_ADMIN);
            user.setStatus(StatusType.ACTIVATED);
        } else {
            TeamleaderCompany matchingCompany = findCompanyByDomain(domain);
            if (matchingCompany != null) {
                logger.info("Assigning COMPANY_USER role to domain match: {} for company: {}",
                        email, matchingCompany.getName());
                roles.add(RoleType.COMPANY_USER);
                user.setStatus(StatusType.PENDING);
            } else {
                logger.info("No role assigned for user: {}", email);
                user.setStatus(StatusType.PENDING);
            }
        }

        user.setRoles(roles);
    }

    private boolean isSystemAdmin(String email, String domain) {
        return userRoleConfig.getSystemAdminDomain().equals(domain) ||
                (userRoleConfig.hasAdminEmail() && userRoleConfig.getSystemAdminEmail().equals(email));
    }

    private boolean isCompanyContactEmail(String email) {
        return teamleaderCompanyService.getAllCompanies().stream()
                .filter(this::hasMyCloudmenAccess)
                .flatMap(company -> getCompanyEmails(company).stream())
                .anyMatch(contactEmail -> contactEmail.equals(email));
    }

    private boolean hasMyCloudmenAccess(TeamleaderCompany company) {
        if (company == null || company.getCustomFields() == null) {
            return false;
        }

        String fieldId = teamleaderConfig.getMyCloudmenAccessFieldId();
        return CustomFieldUtils.isCustomFieldTrue(company.getCustomFields(), fieldId);
    }

    private List<String> getCompanyEmails(TeamleaderCompany company) {
        List<TeamleaderCompany.ContactInfo> contactInfo = company.getContactInfo();
        if (contactInfo == null) {
            return Collections.emptyList();
        }

        return contactInfo.stream()
                .filter(info -> "email-primary".equals(info.getType()))
                .map(TeamleaderCompany.ContactInfo::getValue)
                .collect(Collectors.toList());
    }

    private TeamleaderCompany findCompanyByDomain(String domain) {
        if (domain == null || domain.isEmpty()) {
            return null;
        }

        return teamleaderCompanyService.getAllCompanies().stream()
                .filter(this::hasMyCloudmenAccess)
                .filter(company -> companyHasDomain(company, domain))
                .findFirst()
                .orElse(null);
    }

    private boolean companyHasDomain(TeamleaderCompany company, String domain) {
        return getCompanyEmails(company).stream()
                .map(this::extractDomainFromEmail)
                .anyMatch(domain::equals);
    }

    private String extractDomainFromEmail(String email) {
        if (email == null || email.isEmpty() || !email.contains("@")) {
            return null;
        }

        String[] parts = email.split("@");
        return parts.length > 1 ? parts[1] : null;
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

    private boolean updateUserRoleIfNeeded(User user) {
        List<RoleType> oldRoles = new ArrayList<>(user.getRoles());
        assignUserRole(user);

        if (!oldRoles.equals(user.getRoles())) {
            logger.info("Updating roles for user {}: {} -> {}",
                    user.getEmail(), oldRoles, user.getRoles());
            user.setDateTimeChanged(LocalDateTime.now());
            userRepository.save(user);
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