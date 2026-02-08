package com.quizapp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.dtos.*;
import com.quizapp.enums.AttemptStatus;
import com.quizapp.services.AttemptService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AttemptController.class)
class AttemptControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AttemptService attemptService;

    @Test
    @WithMockUser
    void submitAttempt_returnsAttemptResponse() throws Exception {
        AttemptSubmissionRequest req = new AttemptSubmissionRequest(List.of(
                new AnswerSubmission(1L, null, "Paris"),
                new AnswerSubmission(2L, List.of(10L), null)
        ));

        AttemptResponse response = new AttemptResponse(
                55L,
                5,
                7,
                AttemptStatus.SUBMITTED,
                List.of(
                        new AnswerResult(1L, true, 2),
                        new AnswerResult(2L, true, 3)
                )
        );

        when(attemptService.submitAttempt(eq(10L), org.mockito.ArgumentMatchers.any(AttemptSubmissionRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/events/10/attempts/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(55L))
                .andExpect(jsonPath("$.score").value(5))
                .andExpect(jsonPath("$.maxScore").value(7))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.answers[0].questionId").value(1L))
                .andExpect(jsonPath("$.answers[0].correct").value(true))
                .andExpect(jsonPath("$.answers[0].pointsAwarded").value(2))
                .andExpect(jsonPath("$.answers[1].questionId").value(2L))
                .andExpect(jsonPath("$.answers[1].correct").value(true))
                .andExpect(jsonPath("$.answers[1].pointsAwarded").value(3));

        ArgumentCaptor<AttemptSubmissionRequest> reqCaptor = ArgumentCaptor.forClass(AttemptSubmissionRequest.class);
        verify(attemptService).submitAttempt(eq(10L), reqCaptor.capture());

        AttemptSubmissionRequest captured = reqCaptor.getValue();
        assertEquals(2, captured.answers().size());
        assertEquals(1L, captured.answers().get(0).questionId());
        assertEquals("Paris", captured.answers().get(0).textAnswer());
        assertEquals(2L, captured.answers().get(1).questionId());
        assertEquals(List.of(10L), captured.answers().get(1).selectedOptionIds());
    }

    @Test
    @WithMockUser
    void startAttempt_returnsStartResponse() throws Exception {
        AttemptStartResponse response = new AttemptStartResponse(
                44L,
                AttemptStatus.IN_PROGRESS,
                Instant.parse("2024-01-01T00:00:00Z")
        );

        when(attemptService.startAttempt(10L)).thenReturn(response);

        mockMvc.perform(post("/api/events/10/attempts/start")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.attemptId").value(44L))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.endsAt").value("2024-01-01T00:00:00Z"));

        verify(attemptService).startAttempt(10L);
    }

    @Test
    @WithMockUser
    void cancelAttempt_returnsNoContent() throws Exception {
        mockMvc.perform(post("/api/events/10/attempts/cancel")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(attemptService).cancelAttempt(10L);
    }
}
