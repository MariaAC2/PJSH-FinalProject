package com.quizapp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.dtos.CreateEventRequest;
import com.quizapp.dtos.EventResponse;
import com.quizapp.dtos.JoinEventRequest;
import com.quizapp.dtos.LeaderboardEntry;
import com.quizapp.services.EventService;
import com.quizapp.services.LeaderboardService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;


import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private EventService eventService;
    @MockBean private LeaderboardService leaderboardService;

    @Test
    @WithMockUser
    void createEvent_shouldReturn201_andBody() throws Exception {
        // adjust these fields to match your CreateEventRequest record signature
        CreateEventRequest req = new CreateEventRequest(
                10L,
                "My Event",
                600,
                Instant.parse("2030-01-01T10:00:00Z")
        );

        EventResponse resp = new EventResponse(
                100L,
                10L,
                "My Event",
                "ABCDEFGH",
                null,
                null,
                600,
                "host@test.com",
                "OPEN"
        );

        when(eventService.createEvent(any(CreateEventRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/events")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.testId").value(10))
                .andExpect(jsonPath("$.name").value("My Event"))
                .andExpect(jsonPath("$.joinCode").value("ABCDEFGH"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        verify(eventService).createEvent(any(CreateEventRequest.class));
    }

    @Test
    @WithMockUser
    void join_shouldReturn200_andBody() throws Exception {
        JoinEventRequest req = new JoinEventRequest("ABCDEFGH");

        EventResponse resp = new EventResponse(
                100L,
                10L,
                "My Event",
                "ABCDEFGH",
                null,
                null,
                600,
                "host@test.com",
                "OPEN"
        );

        when(eventService.joinEvent("ABCDEFGH")).thenReturn(resp);

        mockMvc.perform(post("/api/events/join")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.joinCode").value("ABCDEFGH"));

        verify(eventService).joinEvent("ABCDEFGH");
    }

    @Test
    @WithMockUser
    void startEvent_shouldReturn200() throws Exception {
        mockMvc.perform(post("/api/events/123/start")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(eventService).startEvent(123L);
    }

    @Test
    @WithMockUser
    void closeEvent_shouldReturn200() throws Exception {
        mockMvc.perform(post("/api/events/123/close")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(eventService).closeEvent(123L);
    }

    @Test
    @WithMockUser
    void leaveEvent_shouldReturn200() throws Exception {
        mockMvc.perform(post("/api/events/123/leave")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(eventService).leaveEvent(123L);
    }

    @Test
    @WithMockUser
    void cancelEvent_shouldReturn200() throws Exception {
        mockMvc.perform(post("/api/events/123/cancel")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(eventService).cancelEvent(123L);
    }

    @Test
    @WithMockUser
    void listForTest_shouldReturn200_andList() throws Exception {
        List<EventResponse> resp = List.of(
                new EventResponse(1L, 10L, "E1", "CODE1", null, null, 600, "host@test.com", "OPEN"),
                new EventResponse(2L, 10L, "E2", "CODE2", null, null, 600, "host@test.com", "OPEN")
        );

        when(eventService.listEventsForTest(10L)).thenReturn(resp);

        mockMvc.perform(get("/api/events/test/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].joinCode").value("CODE2"));

        verify(eventService).listEventsForTest(10L);
    }

    @Test
    @WithMockUser
    void getEvent_shouldReturn200_andBody() throws Exception {
        EventResponse resp = new EventResponse(
                100L, 10L, "My Event", "ABCDEFGH", null, null, 600, "host@test.com", "OPEN"
        );

        when(eventService.getEventById(100L)).thenReturn(resp);

        mockMvc.perform(get("/api/events/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.name").value("My Event"));

        verify(eventService).getEventById(100L);
    }

    @Test
    @WithMockUser
    void listAllEvents_shouldReturn200_andList() throws Exception {
        when(eventService.listAllEvents()).thenReturn(List.of(
                new EventResponse(1L, 10L, "E1", "C1", null, null, 600, "host@test.com", "OPEN")
        ));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1));

        verify(eventService).listAllEvents();
    }

    @Test
    @WithMockUser
    void leaderboard_shouldReturn200_andList() throws Exception {
        List<LeaderboardEntry> resp = List.of(
                new LeaderboardEntry(1L, "Alice", 10, 15, Instant.parse("2030-01-01T10:00:00Z")),
                new LeaderboardEntry(2L, "Bob", 8, 15, Instant.parse("2030-01-01T10:00:01Z"))
        );

        when(leaderboardService.topForEvent(5L, 3)).thenReturn(resp);

        mockMvc.perform(get("/api/events/5/leaderboard?limit=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].displayName").value("Alice"))
                .andExpect(jsonPath("$[1].score").value(8));

        verify(leaderboardService).topForEvent(5L, 3);
    }
}