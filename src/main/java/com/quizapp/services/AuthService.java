package com.quizapp.services;

import com.quizapp.dtos.LoginRequest;
import com.quizapp.dtos.RegisterRequest;
import com.quizapp.dtos.TokenResponse;
import com.quizapp.entities.User;
import com.quizapp.enums.UserRole;
import com.quizapp.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private final JwtEncoder jwtEncoder;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    public AuthService(JwtEncoder jwtEncoder,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.jwtEncoder = jwtEncoder;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /// Validates email format
    private void validateEmail(String email) {
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email is required");

        if (!EMAIL_PATTERN.matcher(email).matches())
            throw new IllegalArgumentException("Invalid email format");
    }

    /// Validates password strength
    private void validatePassword(String password, String email) {
        if (password == null || password.length() < 8)
            throw new IllegalArgumentException("Password must be at least 8 characters");

        if (!password.matches(".*[A-Z].*"))
            throw new IllegalArgumentException("Password must contain an uppercase letter");

        if (!password.matches(".*[a-z].*"))
            throw new IllegalArgumentException("Password must contain a lowercase letter");

        if (!password.matches(".*\\d.*"))
            throw new IllegalArgumentException("Password must contain a digit");

        if (!password.matches(".*[^a-zA-Z0-9].*"))
            throw new IllegalArgumentException("Password must contain a special character");

        if (password.toLowerCase().contains(email))
            throw new IllegalArgumentException("Password must not contain email");
    }

    /// Registers a new user
    public void register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        String password = request.password();

        validateEmail(email);
        validatePassword(password, email);

        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setDisplayName(request.displayName().trim());

        // Hash password before storing
        user.setPassword(passwordEncoder.encode(request.password()));

        // Default role
        user.setRole(UserRole.USER);

        userRepository.save(user);
    }

    /// Authenticates a user and returns a JWT token
    public TokenResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        JwtClaimsSet.builder()
                                .subject(user.getEmail())
                                .claim("roles", user.getRole().name())
                                .issuedAt(Instant.now())
                                .expiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60))
                                .build()
                )
        ).getTokenValue();

        return new TokenResponse(token);
    }

    public boolean isSelfOrAdmin(Long userId, User currentUser) {
        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;
        boolean isSelf = currentUser.getId() != null && currentUser.getId().equals(userId);
        return isAdmin || isSelf;
    }
}

