package com.quizapp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.dtos.LoginRequest;
import com.quizapp.dtos.RegisterRequest;
import com.quizapp.dtos.TokenResponse;
import com.quizapp.services.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;

    @Test
    void register_returns201_andCallsService() throws Exception {
        RegisterRequest req = new RegisterRequest("john@example.com", "John", "Passw0rd!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(content().string("")); // void endpoint, empty body

        verify(authService).register(any(RegisterRequest.class));
        verifyNoMoreInteractions(authService);
    }

    @Test
    void login_returnsTokenJson() throws Exception {
        LoginRequest req = new LoginRequest("john@example.com", "Passw0rd!");
        when(authService.login(any(LoginRequest.class))).thenReturn(new TokenResponse("TOKEN_ABC"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").value("TOKEN_ABC"));

        verify(authService).login(any(LoginRequest.class));
        verifyNoMoreInteractions(authService);
    }
}
