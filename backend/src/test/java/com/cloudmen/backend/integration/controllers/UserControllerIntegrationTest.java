package com.cloudmen.backend.integration.controllers;

import com.cloudmen.backend.api.dtos.users.UserDTO;
import com.cloudmen.backend.domain.enums.RoleType;
import com.cloudmen.backend.domain.enums.StatusType;
import com.cloudmen.backend.domain.models.User;
import com.cloudmen.backend.services.UserService;
import com.cloudmen.backend.services.UserSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for UserController
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("UserController Integration Tests")
public class UserControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private UserSyncService userSyncService;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .build();
    }

    @Test
    @DisplayName("GET /api/users - Should return all users")
    void getUsers_ShouldReturnAllUsers() throws Exception {
        // Arrange
        User user1 = createTestUser("1", "user1@example.com", "example.com", StatusType.ACTIVATED);
        User user2 = createTestUser("2", "user2@example.com", "example.com", StatusType.ACTIVATED);

        List<User> userList = Arrays.asList(user1, user2);

        when(userService.getAllUsers()).thenReturn(userList);

        // Act & Assert
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is("1")))
                .andExpect(jsonPath("$[1].id", is("2")));
    }

    @Test
    @DisplayName("GET /api/users - Should filter users by domain")
    void getUsers_ShouldFilterUsersByDomain() throws Exception {
        // Arrange
        User user1 = createTestUser("1", "user1@example.com", "example.com", StatusType.ACTIVATED);
        User user2 = createTestUser("2", "user2@test.com", "test.com", StatusType.ACTIVATED);

        List<User> userList = Arrays.asList(user1, user2);

        when(userService.getAllUsers()).thenReturn(userList);

        // Act & Assert
        mockMvc.perform(get("/api/users")
                .param("domain", "example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is("1")))
                .andExpect(jsonPath("$[0].email", is("user1@example.com")));
    }

    @Test
    @DisplayName("GET /api/users - Should filter users by status")
    void getUsers_ShouldFilterUsersByStatus() throws Exception {
        // Arrange
        User user1 = createTestUser("1", "user1@example.com", "example.com", StatusType.ACTIVATED);
        User user2 = createTestUser("2", "user2@example.com", "example.com", StatusType.PENDING);

        List<User> userList = Arrays.asList(user1, user2);

        when(userService.getAllUsers()).thenReturn(userList);

        // Act & Assert
        mockMvc.perform(get("/api/users")
                .param("status", "ACTIVATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is("1")))
                .andExpect(jsonPath("$[0].status", is("ACTIVATED")));
    }

    @Test
    @DisplayName("POST /api/users/register - Should register new user")
    void registerUser_ShouldRegisterNewUser() throws Exception {
        // Arrange
        UserDTO userDTO = new UserDTO();
        userDTO.setEmail("newuser@example.com");
        userDTO.setName("New User");
        userDTO.setAuth0Id("auth0|newuser");

        User newUser = createTestUser("new-id", "newuser@example.com", "example.com", StatusType.PENDING);
        newUser.setAuth0Id("auth0|newuser");

        when(userService.getUserByAuth0Id("auth0|newuser")).thenReturn(Optional.empty());
        when(userService.createUser(any(User.class))).thenReturn(newUser);

        // Act & Assert
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("new-id")))
                .andExpect(jsonPath("$.email", is("newuser@example.com")));
    }

    @Test
    @DisplayName("PUT /api/users/{id} - Should update user")
    void updateUser_ShouldUpdateUser() throws Exception {
        // Arrange
        String userId = "user-id";
        UserDTO userDTO = new UserDTO();
        userDTO.setEmail("updated@example.com");
        userDTO.setName("Updated Name");

        User updatedUser = createTestUser(userId, "updated@example.com", "example.com", StatusType.ACTIVATED);
        updatedUser.setName("Updated Name");

        when(userService.updateUser(eq(userId), any(User.class))).thenReturn(Optional.of(updatedUser));

        // Act & Assert
        mockMvc.perform(put("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(userId)))
                .andExpect(jsonPath("$.email", is("updated@example.com")))
                .andExpect(jsonPath("$.name", is("Updated Name")));
    }

    /**
     * Helper method to create a test user with basic properties
     */
    private User createTestUser(String id, String email, String domain, StatusType status) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPrimaryDomain(domain);
        user.setName("Test User " + id);
        user.setFirstName("Test");
        user.setLastName("User " + id);
        user.setStatus(status);
        user.setRoles(Collections.singletonList(RoleType.COMPANY_USER));
        user.setDateTimeAdded(LocalDateTime.now().minusDays(10));
        user.setDateTimeChanged(LocalDateTime.now());
        return user;
    }
}