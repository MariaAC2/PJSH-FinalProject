package com.quizapp.services;

import com.quizapp.dtos.UserResponse;
import com.quizapp.dtos.UserUpdateRequest;
import com.quizapp.entities.User;
import com.quizapp.enums.UserRole;
import com.quizapp.repositories.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthService authService;

    @InjectMocks UserService userService;

    @Captor ArgumentCaptor<User> userCaptor;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("john@example.com", "N/A")
        );
    }

    @AfterEach
    void tearDownSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User user(Long id, String email, String name, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setDisplayName(name);
        u.setPassword("HASHED");
        u.setRole(role);
        return u;
    }

    @Test
    void getCurrentUser_returnsAuthenticatedUserResponse() {
        User current = user(1L, "john@example.com", "John", UserRole.USER);
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(current));

        UserResponse resp = userService.getCurrentUser();

        assertEquals(1L, resp.id());
        assertEquals("john@example.com", resp.email());
        assertEquals("John", resp.displayName());
        assertEquals(UserRole.USER, resp.role());
    }

    @Test
    void getCurrentUser_whenAuthUserMissingInDb_throws() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.getCurrentUser());
        assertEquals("Authenticated user not found in DB", ex.getMessage());
    }

    @Test
    void listUsers_mapsAllUsersToResponses() {
        when(userRepository.findAll()).thenReturn(List.of(
                user(1L, "a@x.com", "A", UserRole.USER),
                user(2L, "b@x.com", "B", UserRole.ADMIN)
        ));

        List<UserResponse> list = userService.listUsers();

        assertEquals(2, list.size());
        assertEquals("a@x.com", list.get(0).email());
        assertEquals("b@x.com", list.get(1).email());
    }

    @Test
    void getUserById_whenFound_returnsResponse() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(
                user(5L, "x@x.com", "X", UserRole.USER)
        ));

        UserResponse resp = userService.getUserById(5L);

        assertEquals(5L, resp.id());
        assertEquals("x@x.com", resp.email());
    }

    @Test
    void getUserById_whenMissing_throwsIllegalArgument() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.getUserById(99L));

        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void updateUser_whenNotAllowed_throwsAndDoesNotSave() {
        User current = user(1L, "john@example.com", "John", UserRole.USER);
        User target = user(2L, "other@example.com", "Other", UserRole.USER);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(current));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(authService.isSelfOrAdmin(2L, current)).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.updateUser(2L, new UserUpdateRequest("New Name", "NewPass1!")));

        assertEquals("Not allowed to update this user", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_whenAllowed_updatesDisplayNameAndPasswordAndSaves() {
        User current = user(1L, "john@example.com", "John", UserRole.USER);
        User target = user(1L, "john@example.com", "John", UserRole.USER);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(current));
        when(userRepository.findById(1L)).thenReturn(Optional.of(target));
        when(authService.isSelfOrAdmin(1L, current)).thenReturn(true);
        when(passwordEncoder.encode("NewPass1!")).thenReturn("NEW_HASH");

        UserResponse resp = userService.updateUser(1L, new UserUpdateRequest("  John Updated  ", "NewPass1!"));

        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();

        assertEquals("John Updated", saved.getDisplayName());
        assertEquals("NEW_HASH", saved.getPassword());

        assertEquals("john@example.com", resp.email());
        assertEquals("John Updated", resp.displayName());
    }

    @Test
    void deleteUser_whenNotAllowed_throwsAndDoesNotDelete() {
        User current = user(1L, "john@example.com", "John", UserRole.USER);
        User target = user(2L, "other@example.com", "Other", UserRole.USER);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(current));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(authService.isSelfOrAdmin(2L, current)).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.deleteUser(2L));
        // your code currently says "Not allowed to update this user" even on delete
        assertEquals("Not allowed to delete this user", ex.getMessage());

        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteUser_whenAllowed_deletesTarget() {
        User current = user(1L, "john@example.com", "John", UserRole.ADMIN);
        User target = user(2L, "other@example.com", "Other", UserRole.USER);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(current));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(authService.isSelfOrAdmin(2L, current)).thenReturn(true);

        userService.deleteUser(2L);

        verify(userRepository).delete(target);
    }
}
