package com.quizapp.services;

import com.quizapp.dtos.LoginRequest;
import com.quizapp.dtos.RegisterRequest;
import com.quizapp.dtos.TokenResponse;
import com.quizapp.entities.User;
import com.quizapp.enums.UserRole;
import com.quizapp.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock JwtEncoder jwtEncoder;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks AuthService authService;

    @Captor ArgumentCaptor<User> userCaptor;

    @Test
    void register_whenEmailInvalid_throwsAndDoesNotSave() {
        RegisterRequest req = new RegisterRequest("not-an-email", "John", "Passw0rd!");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(req));

        assertEquals("Invalid email format", ex.getMessage());
        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(jwtEncoder);
    }

    @Test
    void register_whenPasswordWeak_throwsAndDoesNotSave() {
        // missing uppercase + special
        RegisterRequest req = new RegisterRequest("john@example.com", "John", "password1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(req));

        // depending on which rule hits first, your message may differ;
        // here it should fail at uppercase rule
        assertEquals("Password must contain an uppercase letter", ex.getMessage());

        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(jwtEncoder);
    }

    @Test
    void register_whenEmailAlreadyRegistered_throwsAndDoesNotSave() {
        RegisterRequest req = new RegisterRequest("  JOHN@EXAMPLE.COM ", "John", "Passw0rd!");
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.register(req));

        assertEquals("Email already registered", ex.getMessage());
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
        verifyNoInteractions(jwtEncoder);
    }

    @Test
    void register_whenValid_savesNormalizedUserWithEncodedPasswordAndDefaultRole() {
        RegisterRequest req = new RegisterRequest("  JOHN@EXAMPLE.COM ", "  John Doe  ", "Passw0rd!");
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("HASHED");

        authService.register(req);

        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();

        assertEquals("john@example.com", saved.getEmail());
        assertEquals("John Doe", saved.getDisplayName());
        assertEquals("HASHED", saved.getPassword());
        assertEquals(UserRole.USER, saved.getRole());

        verify(passwordEncoder).encode("Passw0rd!");
        verifyNoInteractions(jwtEncoder);
    }

    @Test
    void login_whenUserNotFound_throwsInvalidCredentials() {
        LoginRequest req = new LoginRequest("missing@example.com", "Passw0rd!");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.login(req));

        assertEquals("Invalid credentials", ex.getMessage());
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(jwtEncoder);
    }

    @Test
    void login_whenPasswordMismatch_throwsInvalidCredentials() {
        User user = new User();
        user.setEmail("john@example.com");
        user.setPassword("HASHED");
        user.setRole(UserRole.USER);

        LoginRequest req = new LoginRequest("john@example.com", "WrongPass1!");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass1!", "HASHED")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.login(req));

        assertEquals("Invalid credentials", ex.getMessage());
        verify(jwtEncoder, never()).encode(any(JwtEncoderParameters.class));
    }

    @Test
    void login_whenValid_returnsToken() {
        User user = new User();
        user.setEmail("john@example.com");
        user.setPassword("HASHED");
        user.setRole(UserRole.USER);

        LoginRequest req = new LoginRequest("john@example.com", "Passw0rd!");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Passw0rd!", "HASHED")).thenReturn(true);

        // Mock JwtEncoder output
        Jwt jwt = Jwt.withTokenValue("TOKEN_ABC")
                .header("alg", "HS256")
                .claim("sub", "john@example.com")
                .claim("roles", "USER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);

        TokenResponse resp = authService.login(req);

        assertNotNull(resp);
        assertEquals("TOKEN_ABC", resp.token());

        verify(jwtEncoder).encode(any(JwtEncoderParameters.class));
    }
}
