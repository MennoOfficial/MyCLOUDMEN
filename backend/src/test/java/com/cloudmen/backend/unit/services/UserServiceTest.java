package com.cloudmen.backend.unit.services;

import com.cloudmen.backend.domain.enums.RoleType;
import com.cloudmen.backend.domain.enums.StatusType;
import com.cloudmen.backend.domain.models.User;
import com.cloudmen.backend.repositories.UserRepository;
import com.cloudmen.backend.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for UserService using Mockito.
 * Each test focuses on a single function to make tests easier to understand and
 * maintain.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Setup test user
        testUser = new User();
        testUser.setId("user-123");
        testUser.setEmail("test@example.com");
        testUser.setName("Test User");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setPrimaryDomain("example.com");
        testUser.setAuth0Id("auth0|12345");
        testUser.setStatus(StatusType.ACTIVATED);
        testUser.setRoles(Arrays.asList(RoleType.COMPANY_USER));
        testUser.setDateTimeAdded(LocalDateTime.now().minusDays(30));
        testUser.setDateTimeChanged(LocalDateTime.now().minusDays(10));
    }

    @Test
    @DisplayName("getAllUsers should return all users")
    void getAllUsers_shouldReturnAllUsers() {
        // Arrange
        List<User> expectedUsers = Arrays.asList(testUser);
        when(userRepository.findAll()).thenReturn(expectedUsers);

        // Act
        List<User> result = userService.getAllUsers();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("user-123", result.get(0).getId());

        verify(userRepository).findAll();
    }

    @Test
    @DisplayName("getUserById should return user when found")
    void getUserById_shouldReturnUser_whenFound() {
        // Arrange
        when(userRepository.findById(anyString())).thenReturn(Optional.of(testUser));

        // Act
        Optional<User> result = userService.getUserById("user-123");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("user-123", result.get().getId());
        assertEquals("test@example.com", result.get().getEmail());

        verify(userRepository).findById("user-123");
    }

    @Test
    @DisplayName("getUserById should return empty when not found")
    void getUserById_shouldReturnEmpty_whenNotFound() {
        // Arrange
        when(userRepository.findById(anyString())).thenReturn(Optional.empty());

        // Act
        Optional<User> result = userService.getUserById("non-existent");

        // Assert
        assertFalse(result.isPresent());

        verify(userRepository).findById("non-existent");
    }

    @Test
    @DisplayName("getUserByAuth0Id should return user when found")
    void getUserByAuth0Id_shouldReturnUser_whenFound() {
        // Arrange
        when(userRepository.findByAuth0Id(anyString())).thenReturn(Optional.of(testUser));

        // Act
        Optional<User> result = userService.getUserByAuth0Id("auth0|12345");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("auth0|12345", result.get().getAuth0Id());

        verify(userRepository).findByAuth0Id("auth0|12345");
    }

    @Test
    @DisplayName("getUserByEmail should return user when found")
    void getUserByEmail_shouldReturnUser_whenFound() {
        // Arrange
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

        // Act
        Optional<User> result = userService.getUserByEmail("test@example.com");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("test@example.com", result.get().getEmail());

        verify(userRepository).findByEmail("test@example.com");
    }

    @Test
    @DisplayName("createUser should save and return user")
    void createUser_shouldSaveAndReturnUser() {
        // Arrange
        User newUser = new User();
        newUser.setEmail("new@example.com");
        newUser.setName("New User");

        when(userRepository.save(any(User.class))).thenReturn(newUser);

        // Act
        User result = userService.createUser(newUser);

        // Assert
        assertNotNull(result);
        assertEquals("new@example.com", result.getEmail());

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("updateUser should update existing user")
    void updateUser_shouldUpdateExistingUser() {
        // Arrange
        User updatedUser = new User();
        updatedUser.setEmail("updated@example.com");
        updatedUser.setName("Updated Name");
        updatedUser.setStatus(StatusType.DEACTIVATED);

        when(userRepository.findById(anyString())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        Optional<User> result = userService.updateUser("user-123", updatedUser);

        // Assert
        assertTrue(result.isPresent());
        User resultUser = result.get();
        assertEquals("updated@example.com", resultUser.getEmail());
        assertEquals("Updated Name", resultUser.getName());
        assertEquals(StatusType.DEACTIVATED, resultUser.getStatus());

        verify(userRepository).findById("user-123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("updateUser should only update non-null fields")
    void updateUser_shouldOnlyUpdateNonNullFields() {
        // Arrange
        User partialUpdate = new User();
        partialUpdate.setName("New Name");
        // Other fields are null - should not be updated

        when(userRepository.findById(anyString())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Optional<User> result = userService.updateUser("user-123", partialUpdate);

        // Assert
        assertTrue(result.isPresent());
        User resultUser = result.get();
        assertEquals("New Name", resultUser.getName());
        assertEquals("test@example.com", resultUser.getEmail()); // Should not be changed
        assertEquals(StatusType.ACTIVATED, resultUser.getStatus()); // Should not be changed

        verify(userRepository).findById("user-123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("updateUser should return empty when user not found")
    void updateUser_shouldReturnEmpty_whenUserNotFound() {
        // Arrange
        User updatedUser = new User();
        updatedUser.setEmail("updated@example.com");

        when(userRepository.findById(anyString())).thenReturn(Optional.empty());

        // Act
        Optional<User> result = userService.updateUser("non-existent", updatedUser);

        // Assert
        assertFalse(result.isPresent());

        verify(userRepository).findById("non-existent");
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("deleteUser should call repository")
    void deleteUser_shouldCallRepository() {
        // Act
        userService.deleteUser("user-123");

        // Assert
        verify(userRepository).deleteById("user-123");
    }

    @Test
    @DisplayName("updateUser should properly update user roles")
    void updateUser_shouldProperlyUpdateUserRoles() {
        // Arrange
        String userId = "existing-id";
        User existingUser = createTestUser(userId, "test@example.com");
        existingUser.setRoles(Collections.singletonList(RoleType.COMPANY_USER));

        User updatedDetails = new User();
        updatedDetails.setRoles(Arrays.asList(RoleType.COMPANY_USER, RoleType.COMPANY_ADMIN));

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Optional<User> result = userService.updateUser(userId, updatedDetails);

        // Assert
        assertTrue(result.isPresent());
        User updatedUser = result.get();
        assertEquals(2, updatedUser.getRoles().size());
        assertTrue(updatedUser.getRoles().contains(RoleType.COMPANY_USER));
        assertTrue(updatedUser.getRoles().contains(RoleType.COMPANY_ADMIN));

        // Verify repository interactions
        verify(userRepository).findById(userId);
        verify(userRepository).save(existingUser);
    }

    @Test
    @DisplayName("updateUser should properly update user status")
    void updateUser_shouldProperlyUpdateUserStatus() {
        // Arrange
        String userId = "existing-id";
        User existingUser = createTestUser(userId, "test@example.com");
        existingUser.setStatus(StatusType.ACTIVATED);

        User updatedDetails = new User();
        updatedDetails.setStatus(StatusType.DEACTIVATED);

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Optional<User> result = userService.updateUser(userId, updatedDetails);

        // Assert
        assertTrue(result.isPresent());
        User updatedUser = result.get();
        assertEquals(StatusType.DEACTIVATED, updatedUser.getStatus());

        // Verify repository interactions
        verify(userRepository).findById(userId);
        verify(userRepository).save(existingUser);
    }

    @Test
    @DisplayName("updateUser should properly update primaryDomain")
    void updateUser_shouldProperlyUpdatePrimaryDomain() {
        // Arrange
        String userId = "existing-id";
        User existingUser = createTestUser(userId, "test@example.com");
        existingUser.setPrimaryDomain("domain1");

        User updatedDetails = new User();
        updatedDetails.setPrimaryDomain("domain2");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Optional<User> result = userService.updateUser(userId, updatedDetails);

        // Assert
        assertTrue(result.isPresent());
        User updatedUser = result.get();
        assertEquals("domain2", updatedUser.getPrimaryDomain());

        // Verify repository interactions
        verify(userRepository).findById(userId);
        verify(userRepository).save(existingUser);
    }

    @Test
    @DisplayName("updateUser should properly update firstName")
    void updateUser_shouldProperlyUpdateFirstName() {
        // Arrange
        String userId = "existing-id";
        User existingUser = createTestUser(userId, "test@example.com");
        existingUser.setFirstName("John");

        User updatedDetails = new User();
        updatedDetails.setFirstName("Jane");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Optional<User> result = userService.updateUser(userId, updatedDetails);

        // Assert
        assertTrue(result.isPresent());
        User updatedUser = result.get();
        assertEquals("Jane", updatedUser.getFirstName());

        // Verify repository interactions
        verify(userRepository).findById(userId);
        verify(userRepository).save(existingUser);
    }

    @Test
    @DisplayName("updateUser should properly update lastName")
    void updateUser_shouldProperlyUpdateLastName() {
        // Arrange
        String userId = "existing-id";
        User existingUser = createTestUser(userId, "test@example.com");
        existingUser.setLastName("Doe");

        User updatedDetails = new User();
        updatedDetails.setLastName("Smith");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Optional<User> result = userService.updateUser(userId, updatedDetails);

        // Assert
        assertTrue(result.isPresent());
        User updatedUser = result.get();
        assertEquals("Smith", updatedUser.getLastName());

        // Verify repository interactions
        verify(userRepository).findById(userId);
        verify(userRepository).save(existingUser);
    }

    @Test
    @DisplayName("updateUser should properly update picture")
    void updateUser_shouldProperlyUpdatePicture() {
        // Arrange
        String userId = "existing-id";
        User existingUser = createTestUser(userId, "test@example.com");
        existingUser.setPicture("old-picture-url");

        User updatedDetails = new User();
        updatedDetails.setPicture("new-picture-url");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Optional<User> result = userService.updateUser(userId, updatedDetails);

        // Assert
        assertTrue(result.isPresent());
        User updatedUser = result.get();
        assertEquals("new-picture-url", updatedUser.getPicture());

        // Verify repository interactions
        verify(userRepository).findById(userId);
        verify(userRepository).save(existingUser);
    }

    /**
     * Helper method to create a test user with basic properties
     */
    private User createTestUser(String id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setDateTimeAdded(LocalDateTime.now());
        user.setDateTimeChanged(LocalDateTime.now());
        return user;
    }
}