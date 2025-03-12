package com.cloudmen.backend.services;

import com.cloudmen.backend.domain.models.User;
import com.cloudmen.backend.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(String id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByAuth0Id(String auth0Id) {
        return userRepository.findByAuth0Id(auth0Id);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User createUser(User user) {
        user.prePersist();
        return userRepository.save(user);
    }

    public Optional<User> updateUser(String id, User userDetails) {
        return userRepository.findById(id)
                .map(user -> {
                    // Only update fields that are not null in userDetails
                    if (userDetails.getEmail() != null) {
                        user.setEmail(userDetails.getEmail());
                    }
                    if (userDetails.getRoles() != null) {
                        user.setRoles(userDetails.getRoles());
                    }
                    if (userDetails.getStatus() != null) {
                        user.setStatus(userDetails.getStatus());
                    }
                    if (userDetails.getPrimaryDomain() != null) {
                        user.setPrimaryDomain(userDetails.getPrimaryDomain());
                    }
                    if (userDetails.getName() != null) {
                        user.setName(userDetails.getName());
                    }
                    if (userDetails.getFirstName() != null) {
                        user.setFirstName(userDetails.getFirstName());
                    }
                    if (userDetails.getLastName() != null) {
                        user.setLastName(userDetails.getLastName());
                    }
                    if (userDetails.getPicture() != null) {
                        user.setPicture(userDetails.getPicture());
                    }

                    user.setDateTimeChanged(LocalDateTime.now());
                    return userRepository.save(user);
                });
    }

    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }
}