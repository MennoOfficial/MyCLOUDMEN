package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.api.dtos.UserDTO;
import com.cloudmen.backend.api.dtos.UserResponseDTO;
import com.cloudmen.backend.domain.enums.RoleType;
import com.cloudmen.backend.domain.enums.StatusType;
import com.cloudmen.backend.domain.models.User;
import com.cloudmen.backend.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{auth0Id}")
    public ResponseEntity<User> getUserByAuth0Id(@PathVariable String auth0Id) {
        return userService.getUserByAuth0Id(auth0Id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/exists/{auth0Id}")
    public ResponseEntity<Boolean> checkUserExists(@PathVariable String auth0Id) {
        boolean exists = userService.getUserByAuth0Id(auth0Id).isPresent();
        return ResponseEntity.ok(exists);
    }

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

    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> registerUser(@RequestBody UserDTO userDTO) {
        // Extract domain from email
        String email = userDTO.getEmail();
        String domain = email.substring(email.indexOf('@') + 1);

        // Create user with default COMPANY_USER role
        User newUser = new User();
        newUser.setAuth0Id(userDTO.getAuth0Id()); // Store Auth0 ID
        newUser.setEmail(userDTO.getEmail());
        newUser.setName(userDTO.getName());
        newUser.setFirstName(userDTO.getFirstName());
        newUser.setLastName(userDTO.getLastName());
        newUser.setPicture(userDTO.getPicture());
        newUser.setRoles(Arrays.asList(RoleType.COMPANY_USER));
        newUser.setStatus(StatusType.ACTIVATED);
        newUser.setPrimaryDomain(domain);
        newUser.setDateTimeAdded(LocalDateTime.now());

        if (userDTO.getProvider() != null && userDTO.getProvider().equals("Google")
                && userDTO.getCustomerGoogleId() != null) {
            newUser.setCustomerGoogleId(userDTO.getCustomerGoogleId());
        }

        User savedUser = userService.createUser(newUser);

        UserResponseDTO response = new UserResponseDTO(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRoles(),
                savedUser.getAuth0Id());

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable String id, @RequestBody User user) {
        return userService.updateUser(id, user)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}