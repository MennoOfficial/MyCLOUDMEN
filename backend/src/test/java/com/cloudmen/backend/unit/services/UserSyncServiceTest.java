package com.cloudmen.backend.unit.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import com.cloudmen.backend.utils.CustomFieldUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.MockitoAnnotations;

import com.cloudmen.backend.config.TeamleaderConfig;
import com.cloudmen.backend.config.UserRoleConfig;
import com.cloudmen.backend.domain.enums.RoleType;
import com.cloudmen.backend.domain.enums.StatusType;
import com.cloudmen.backend.domain.models.TeamleaderCompany;
import com.cloudmen.backend.domain.models.User;
import com.cloudmen.backend.repositories.UserRepository;
import com.cloudmen.backend.services.TeamleaderCompanyService;
import com.cloudmen.backend.services.UserService;
import com.cloudmen.backend.services.UserSyncService;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class UserSyncServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private TeamleaderCompanyService companyService;

    @Mock
    private UserRoleConfig userRoleConfig;

    @Mock
    private TeamleaderConfig teamleaderConfig;

    @InjectMocks
    private UserSyncService userSyncService;

    private User testUser;
    private TeamleaderCompany testCompany;
    private Map<String, Object> auth0Data;

    @BeforeEach
    void setUp() {
        // Setup TeamleaderConfig mock with lenient mode to avoid unnecessary stubbing
        // errors
        lenient().when(teamleaderConfig.getMyCloudmenAccessFieldId()).thenReturn("cloudmen-access-field");

        // Setup UserRoleConfig mock - no need to mock roles as they're used directly as
        // RoleType enums
        lenient().when(userRoleConfig.getSystemAdminDomain()).thenReturn("cloudmen.io");
        lenient().when(userRoleConfig.hasAdminEmail()).thenReturn(false);

        // Setup test company
        testCompany = new TeamleaderCompany();
        testCompany.setId("company-123");
        testCompany.setTeamleaderId("tl-123");
        testCompany.setName("Test Company");

        // Set up contact info
        List<TeamleaderCompany.ContactInfo> contactInfo = new ArrayList<>();
        TeamleaderCompany.ContactInfo emailContact = new TeamleaderCompany.ContactInfo();
        emailContact.setType("email-primary");
        emailContact.setValue("contact@example.com");
        contactInfo.add(emailContact);
        testCompany.setContactInfo(contactInfo);

        // Setup custom fields
        Map<String, Object> customFields = new HashMap<>();
        customFields.put("cloudmen-access-field", true);
        testCompany.setCustomFields(customFields);

        // Setup mock responses for companies with lenient mode
        lenient().when(companyService.getAllCompanies()).thenReturn(Collections.singletonList(testCompany));

        // Initialize test user data
        testUser = new User();
        testUser.setId("user-123");
        testUser.setEmail("test@example.com");
        testUser.setName("Test User");
        testUser.setRoles(new ArrayList<>());

        // Initialize auth0Data
        auth0Data = new HashMap<>();
        auth0Data.put("sub", "auth0|12345");
        auth0Data.put("name", "Test User");
        auth0Data.put("email", "test@example.com");

        // Setup common service behavior - not used in all tests, so use lenient mode
        lenient().when(userService.getUserByEmail(eq("test@example.com"))).thenReturn(Optional.of(testUser));

        // Common configuration for most tests
        userSyncService = new UserSyncService(
                userRepository,
                userService,
                companyService,
                userRoleConfig,
                teamleaderConfig);
    }

    @Test
    @DisplayName("syncUserWithAuth0 should update existing user")
    void syncUserWithAuth0_shouldUpdateExistingUser() {
        // Arrange
        when(userService.getUserByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        User result = userSyncService.syncUserWithAuth0("test@example.com", auth0Data);

        // Assert
        assertNotNull(result);
        assertEquals("auth0|12345", result.getAuth0Id());
        assertEquals("Test User", result.getName());

        verify(userService).getUserByEmail("test@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("syncUserWithAuth0 should create new user when not found")
    void syncUserWithAuth0_shouldCreateNewUser_whenNotFound() {
        // Arrange
        when(userService.getUserByEmail(anyString())).thenReturn(Optional.empty());
        when(userRoleConfig.hasAdminEmail()).thenReturn(false);
        when(companyService.getAllCompanies()).thenReturn(Collections.emptyList());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<CustomFieldUtils> mockCustomFieldUtils = mockStatic(CustomFieldUtils.class)) {
            mockCustomFieldUtils.when(() -> CustomFieldUtils.isCustomFieldTrue(anyMap(), anyString())).thenReturn(true);

            // Act
            User result = userSyncService.syncUserWithAuth0("new@example.com", auth0Data);

            // Assert
            assertNotNull(result);
            assertEquals("auth0|12345", result.getAuth0Id());
            assertEquals("Test User", result.getName());
            assertEquals("example.com", result.getPrimaryDomain());
            assertEquals(StatusType.PENDING, result.getStatus());

            verify(userService).getUserByEmail("new@example.com");
            verify(userRepository).save(any(User.class));
        }
    }

    @Test
    @DisplayName("assignUserRole should assign SYSTEM_ADMIN role for admin domain")
    void assignUserRole_shouldAssignSystemAdminRole_forAdminDomain() {
        // Arrange
        User adminUser = new User();
        adminUser.setEmail("admin@admin.com");
        adminUser.setPrimaryDomain("admin.com");
        adminUser.setRoles(new ArrayList<>());

        // Set the admin domain to match the test user's domain
        when(userRoleConfig.getSystemAdminDomain()).thenReturn("admin.com");

        // Act
        userSyncService.assignUserRole(adminUser);

        // Assert
        assertFalse(adminUser.getRoles().isEmpty());
        assertEquals(RoleType.SYSTEM_ADMIN, adminUser.getRoles().get(0));
        assertEquals(StatusType.ACTIVATED, adminUser.getStatus());
    }

    @Test
    @DisplayName("assignUserRole should assign COMPANY_ADMIN role for company contact")
    void assignUserRole_shouldAssignCompanyAdminRole_forCompanyContact() {
        // Arrange
        User contactUser = new User();
        contactUser.setEmail("contact@example.com");
        contactUser.setPrimaryDomain("example.com");
        contactUser.setRoles(new ArrayList<>());

        when(userRoleConfig.hasAdminEmail()).thenReturn(false);
        when(companyService.getAllCompanies()).thenReturn(Collections.singletonList(testCompany));

        try (MockedStatic<CustomFieldUtils> mockCustomFieldUtils = mockStatic(CustomFieldUtils.class)) {
            mockCustomFieldUtils.when(() -> CustomFieldUtils.isCustomFieldTrue(anyMap(), anyString())).thenReturn(true);

            // Act
            userSyncService.assignUserRole(contactUser);

            // Assert
            assertTrue(!contactUser.getRoles().isEmpty(), "Contact user should have a role assigned");
            assertEquals(RoleType.COMPANY_ADMIN, contactUser.getRoles().get(0));
            assertEquals(StatusType.ACTIVATED, contactUser.getStatus());
        }
    }

    @Test
    @DisplayName("assignUserRole should assign COMPANY_ADMIN role for matching domain")
    void assignUserRole_shouldAssignCompanyUserRole_forMatchingDomain() {
        // Arrange
        User user = new User();
        user.setEmail("user@example.com");
        user.setRoles(new ArrayList<>());

        // Act
        userSyncService.assignUserRole(user);

        // Assert - user should get COMPANY_ADMIN role since email domain matches the
        // company contact
        assertEquals(1, user.getRoles().size());
        assertEquals(RoleType.COMPANY_ADMIN, user.getRoles().get(0));
    }

    @Test
    @DisplayName("updateExistingUserRoles should update roles for users")
    void updateExistingUserRoles_shouldUpdateRolesForUsers() {
        // Arrange
        User user1 = new User();
        user1.setEmail("user1@example.com");
        user1.setRoles(new ArrayList<>());

        User user2 = new User();
        user2.setEmail("user2@other.com");
        user2.setRoles(new ArrayList<>());

        List<User> allUsers = Arrays.asList(user1, user2);

        when(userRepository.findAll()).thenReturn(allUsers);

        // Act
        userSyncService.updateExistingUserRoles();

        // Assert - only one user (user1) should get updated since it matches the
        // company domain
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("removeRolesFromIneligibleUsers should remove roles from ineligible users")
    void removeRolesFromIneligibleUsers_shouldRemoveRolesFromIneligibleUsers() {
        // Arrange
        User user1 = new User();
        user1.setEmail("user1@example.com");
        user1.setPrimaryDomain("example.com");
        user1.setRoles(Collections.singletonList(RoleType.COMPANY_USER));

        User user2 = new User();
        user2.setEmail("user2@inactive.com");
        user2.setPrimaryDomain("inactive.com");
        user2.setRoles(Collections.singletonList(RoleType.COMPANY_USER));

        List<User> allUsers = Arrays.asList(user1, user2);

        // Company for user1 with access
        TeamleaderCompany activeCompany = new TeamleaderCompany();
        activeCompany.setId("active-123");
        activeCompany.setName("Active Company");
        List<TeamleaderCompany.ContactInfo> activeContacts = new ArrayList<>();
        TeamleaderCompany.ContactInfo activeContact = new TeamleaderCompany.ContactInfo();
        activeContact.setType("email-primary");
        activeContact.setValue("contact@example.com");
        activeContacts.add(activeContact);
        activeCompany.setContactInfo(activeContacts);
        Map<String, Object> activeCustomFields = new HashMap<>();
        activeCustomFields.put("cloudmen-access-field", true);
        activeCompany.setCustomFields(activeCustomFields);

        when(userRepository.findAll()).thenReturn(allUsers);
        when(companyService.getAllCompanies()).thenReturn(Collections.singletonList(activeCompany));
        when(userRepository.save(any(User.class))).thenReturn(user2);

        try (MockedStatic<CustomFieldUtils> mockCustomFieldUtils = mockStatic(CustomFieldUtils.class)) {
            mockCustomFieldUtils.when(() -> CustomFieldUtils.isCustomFieldTrue(anyMap(), anyString())).thenReturn(true);

            // Act
            userSyncService.removeRolesFromIneligibleUsers();

            // Assert - we can't check the user roles directly here since the method
            // modifies the in-memory objects
            verify(userRepository).findAll();
            verify(userRepository, times(1)).save(any(User.class)); // Only user2 should be updated
        }
    }
}