package com.quizapp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.dtos.UserResponse;
import com.quizapp.dtos.UserUpdateRequest;
import com.quizapp.enums.UserRole;
import com.quizapp.services.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(UserControllerTest.MockConfig.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService; // the mocked bean from MockConfig

    @TestConfiguration
    static class MockConfig {
        @Bean
        UserService userService() {
            return Mockito.mock(UserService.class);
        }
    }

    @Test
    @WithMockUser
    void currentUser_returnsJson() throws Exception {
        when(userService.getCurrentUser()).thenReturn(
                new UserResponse(1L, "john@example.com", "John", UserRole.USER)
        );

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.displayName").value("John"))
                .andExpect(jsonPath("$.role").value("USER"));

        verify(userService).getCurrentUser();
    }

    @Test
    @WithMockUser
    void listUsers_returnsJsonArray() throws Exception {
        when(userService.listUsers()).thenReturn(List.of(
                new UserResponse(1L, "a@x.com", "A", UserRole.USER),
                new UserResponse(2L, "b@x.com", "B", UserRole.ADMIN)
        ));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("a@x.com"))
                .andExpect(jsonPath("$[1].role").value("ADMIN"));

        verify(userService).listUsers();
    }

    @Test
    @WithMockUser
    void getUserById_returnsJson() throws Exception {
        when(userService.getUserById(5L)).thenReturn(
                new UserResponse(5L, "x@x.com", "X", UserRole.USER)
        );

        mockMvc.perform(get("/api/users/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.email").value("x@x.com"));

        verify(userService).getUserById(5L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateUser_returnsUpdatedJson() throws Exception {
        UserUpdateRequest req = new UserUpdateRequest("New Name", "NewPass1!");
        when(userService.updateUser(eq(7L), any(UserUpdateRequest.class)))
                .thenReturn(new UserResponse(7L, "u@x.com", "New Name", UserRole.USER));

        mockMvc.perform(put("/api/users/7")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.displayName").value("New Name"));

        verify(userService).updateUser(eq(7L), any(UserUpdateRequest.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteUser_callsService_andReturns200() throws Exception {
        mockMvc.perform(delete("/api/users/9")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(userService).deleteUser(9L);
    }
}
