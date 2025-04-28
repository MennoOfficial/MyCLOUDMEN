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
import org.mockito.stubbing.Answer;

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
        // Setup test user
        testUser = new User();
        testUser.setId("user-123");
        testUser.setEmail("test@example.com");
        testUser.setName("Test User");
        testUser.setPrimaryDomain("example.com");
        testUser.setRoles(new ArrayList<>());
        testUser.setStatus(StatusType.PENDING);

        // Setup test company
        testCompany = new TeamleaderCompany();
        testCompany.setId("company-123");
        testCompany.setTeamleaderId("tl-123");
        testCompany.setName("Test Company");

        // Setup test company contact info
        List<TeamleaderCompany.ContactInfo> contactInfo = new ArrayList<>();
        TeamleaderCompany.ContactInfo emailContact = new TeamleaderCompany.ContactInfo();
        emailContact.setType("email-primary");
        emailContact.setValue("contact@example.com");
        contactInfo.add(emailContact);
        testCompany.setContactInfo(contactInfo);

        // Setup custom fields for company
        Map<String, Object> customFields = new HashMap<>();
        customFields.put("cloudmen-access-field", true);
        testCompany.setCustomFields(customFields);

        // Setup Auth0 data
        auth0Data = new HashMap<>();
        auth0Data.put("sub", "auth0|12345");
        auth0Data.put("name", "Test User");
        auth0Data.put("given_name", "Test");
        auth0Data.put("family_name", "User");
        auth0Data.put("picture", "https://example.com/picture.jpg");
        auth0Data.put("email", "test@example.com");

        // Mock CustomFieldUtils to return true for our test company
        try (MockedStatic<CustomFieldUtils> mockCustomFieldUtils = mockStatic(CustomFieldUtils.class)) {
            mockCustomFieldUtils.when(() -> CustomFieldUtils.isCustomFieldTrue(anyMap(), anyString())).thenReturn(true);
        }
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
        lenient().when(userRoleConfig.getSystemAdminDomain()).thenReturn("admin.com");
        lenient().when(userRoleConfig.hasAdminEmail()).thenReturn(false);
        lenient().when(companyService.getAllCompanies()).thenReturn(Collections.emptyList());
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

        when(userRoleConfig.getSystemAdminDomain()).thenReturn("admin.com");
        when(userRoleConfig.hasAdminEmail()).thenReturn(false);
        when(companyService.getAllCompanies()).thenReturn(Collections.singletonList(testCompany));
        when(teamleaderConfig.getMyCloudmenAccessFieldId()).thenReturn("cloudmen-access-field");

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
    @DisplayName("assignUserRole should assign COMPANY_USER role for matching domain")
    void assignUserRole_shouldAssignCompanyUserRole_forMatchingDomain() {
        // Arrange
        User domainUser = new User();
        domainUser.setEmail("user@example.com");
        domainUser.setPrimaryDomain("example.com");
        domainUser.setRoles(new ArrayList<>());

        // Add contact email with matching domain
        TeamleaderCompany.ContactInfo emailWithMatchingDomain = new TeamleaderCompany.ContactInfo();
        emailWithMatchingDomain.setType("email-primary");
        emailWithMatchingDomain.setValue("someone@example.com");
        testCompany.getContactInfo().add(emailWithMatchingDomain);

        when(userRoleConfig.getSystemAdminDomain()).thenReturn("admin.com");
        when(userRoleConfig.hasAdminEmail()).thenReturn(false);
        when(companyService.getAllCompanies()).thenReturn(Collections.singletonList(testCompany));
        when(teamleaderConfig.getMyCloudmenAccessFieldId()).thenReturn("cloudmen-access-field");

        try (MockedStatic<CustomFieldUtils> mockCustomFieldUtils = mockStatic(CustomFieldUtils.class)) {
            mockCustomFieldUtils.when(() -> CustomFieldUtils.isCustomFieldTrue(anyMap(), anyString())).thenReturn(true);

            // Act
            userSyncService.assignUserRole(domainUser);

            // Assert
            assertTrue(!domainUser.getRoles().isEmpty(), "Domain user should have a role assigned");
            assertEquals(RoleType.COMPANY_USER, domainUser.getRoles().get(0));
            assertEquals(StatusType.PENDING, domainUser.getStatus());
        }
    }

    @Test
    @DisplayName("updateExistingUserRoles should update roles for users")
    void updateExistingUserRoles_shouldUpdateRolesForUsers() {
        // Arrange
        User user1 = new User();
        user1.setEmail("user1@example.com");
        user1.setPrimaryDomain("example.com");
        user1.setRoles(Collections.emptyList());

        User user2 = new User();
        user2.setEmail("user2@other.com");
        user2.setPrimaryDomain("other.com");
        user2.setRoles(Collections.emptyList());

        List<User> allUsers = Arrays.asList(user1, user2);

        // Add contact email with matching domain
        TeamleaderCompany.ContactInfo emailWithMatchingDomain = new TeamleaderCompany.ContactInfo();
        emailWithMatchingDomain.setType("email-primary");
        emailWithMatchingDomain.setValue("someone@example.com");
        testCompany.getContactInfo().add(emailWithMatchingDomain);

        when(userRepository.findAll()).thenReturn(allUsers);
        when(userRoleConfig.getSystemAdminDomain()).thenReturn("admin.com");
        when(userRoleConfig.hasAdminEmail()).thenReturn(false);
        when(companyService.getAllCompanies()).thenReturn(Collections.singletonList(testCompany));
        when(teamleaderConfig.getMyCloudmenAccessFieldId()).thenReturn("cloudmen-access-field");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<CustomFieldUtils> mockCustomFieldUtils = mockStatic(CustomFieldUtils.class)) {
            mockCustomFieldUtils.when(() -> CustomFieldUtils.isCustomFieldTrue(anyMap(), anyString())).thenReturn(true);

            // Act
            int updatedCount = userSyncService.updateExistingUserRoles();

            // Assert
            assertEquals(1, updatedCount, "Should have updated one user");

            verify(userRepository).findAll();
            verify(userRepository, times(1)).save(any(User.class));
        }
    }

    @Test
    @DisplayName("removeRolesFromIneligibleUsers should remove roles from ineligible users")
    void removeRolesFromIneligibleUsers_shouldRemoveRolesFromIneligibleUsers() {
        // Arrange
        User ineligibleUser = new User();
        ineligibleUser.setEmail("user@noaccess.com");
        ineligibleUser.setPrimaryDomain("noaccess.com");
        ineligibleUser.setRoles(Collections.singletonList(RoleType.COMPANY_USER));

        when(userRepository.findAll()).thenReturn(Collections.singletonList(ineligibleUser));
        when(userRoleConfig.getSystemAdminDomain()).thenReturn("admin.com");
        when(userRoleConfig.hasAdminEmail()).thenReturn(false);
        when(companyService.getAllCompanies()).thenReturn(Collections.singletonList(testCompany));
        when(teamleaderConfig.getMyCloudmenAccessFieldId()).thenReturn("cloudmen-access-field");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<CustomFieldUtils> mockCustomFieldUtils = mockStatic(CustomFieldUtils.class)) {
            mockCustomFieldUtils.when(() -> CustomFieldUtils.isCustomFieldTrue(anyMap(), anyString())).thenReturn(true);

            // Act
            int removedCount = userSyncService.removeRolesFromIneligibleUsers();

            // Assert
            assertEquals(1, removedCount);
            assertTrue(ineligibleUser.getRoles().isEmpty());

            verify(userRepository).findAll();
            verify(userRepository).save(any(User.class));
        }
    }
}