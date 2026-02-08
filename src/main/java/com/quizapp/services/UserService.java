package com.quizapp.services;

import com.quizapp.dtos.UserResponse;
import com.quizapp.dtos.UserUpdateRequest;
import com.quizapp.entities.User;
import com.quizapp.enums.UserRole;
import com.quizapp.repositories.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthService authService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    public User getAuthenticatedUserEntity() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found in DB"));
    }

    /// Returns the currently authenticated user's details
    public UserResponse getCurrentUser() {
        User u = getAuthenticatedUserEntity();
        return toResponse(u);
    }

    /// Lists all users (admin only)
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /// Gets user by ID
    public UserResponse getUserById(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toResponse(u);
    }

    /// Updates user: only ADMIN or own user can update
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User current = getAuthenticatedUserEntity();

        User target = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Authorization rule: admin OR self
        if (!authService.isSelfOrAdmin(id, current)) {
            throw new RuntimeException("Not allowed to update this user");
        }

        // Update displayName (optional)
        if (request.displayName() != null && !request.displayName().isBlank()) {
            target.setDisplayName(request.displayName().trim());
        }

        // Update password (optional)
        if (request.password() != null && !request.password().isBlank()) {
            // You can enforce your password rules here if you want consistency with AuthService
            target.setPassword(passwordEncoder.encode(request.password()));
        }

        userRepository.save(target);
        return toResponse(target);
    }

    /// Deletes user: only ADMIN or own user can delete
    public void deleteUser(Long id) {
        User current = getAuthenticatedUserEntity();

        User target = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!authService.isSelfOrAdmin(id, current)) {
            throw new RuntimeException("Not allowed to delete this user");
        }

        userRepository.delete(target);
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole());
    }
}
