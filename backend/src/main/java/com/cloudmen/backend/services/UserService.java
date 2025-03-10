package com.cloudmen.backend.services;

import com.cloudmen.backend.domain.models.User;
import com.cloudmen.backend.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(String id) {
        return userRepository.findById(id);
    }

    public User createUser(User user) {
        user.setDateTimeAdded(LocalDateTime.now());
        return userRepository.save(user);
    }

    public Optional<User> updateUser(String id, User userDetails) {
        return userRepository.findById(id).map(existingUser -> {
            if (userDetails.getEmail() != null) {
                existingUser.setEmail(userDetails.getEmail());
            }
            if (userDetails.getRole() != null) {
                existingUser.setRole(userDetails.getRole());
            }
            if (userDetails.getStatus() != null) {
                existingUser.setStatus(userDetails.getStatus());
            }
            existingUser.setDateTimeChanged(LocalDateTime.now());
            return userRepository.save(existingUser);
        });
    }

    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }
}
