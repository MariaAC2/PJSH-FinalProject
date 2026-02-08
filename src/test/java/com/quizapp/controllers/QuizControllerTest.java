package com.quizapp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.dtos.*;
import com.quizapp.enums.QuestionType;
import com.quizapp.services.QuizService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QuizController.class)
class QuizControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private QuizService quizService;

    @Test
    @WithMockUser
    void createTest_shouldReturn201_andLocationHeader() throws Exception {
        CreateQuizRequest req = sampleCreateTestRequest();

        QuizResponse resp = new QuizResponse(
                123L,
                "My Quiz",
                "Desc",
                List.of()
        );

        when(quizService.createQuiz(any(CreateQuizRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/quizzes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(123))
                .andExpect(jsonPath("$.title").value("My Quiz"));

        verify(quizService).createQuiz(any(CreateQuizRequest.class));
    }

    @Test
    @WithMockUser
    void getTest_shouldReturn200() throws Exception {
        QuizResponse resp = new QuizResponse(10L, "Title", "Desc", List.of());
        when(quizService.getQuiz(10L)).thenReturn(resp);

        mockMvc.perform(get("/api/quizzes/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("Title"));

        verify(quizService).getQuiz(10L);
    }

    @Test
    @WithMockUser
    void updateTest_shouldReturn200() throws Exception {
        CreateQuizRequest req = sampleCreateTestRequest();
        QuizResponse resp = new QuizResponse(10L, "Updated", "Desc", List.of());

        when(quizService.updateQuiz(Mockito.eq(10L), any(CreateQuizRequest.class))).thenReturn(resp);

        mockMvc.perform(put("/api/quizzes/10")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("Updated"));

        verify(quizService).updateQuiz(Mockito.eq(10L), any(CreateQuizRequest.class));
    }

    @Test
    @WithMockUser
    void deleteTest_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/quizzes/10")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(quizService).deleteQuiz(10L);
    }

    private static CreateQuizRequest sampleCreateTestRequest() {
        CreateQuestionRequest q1 = new CreateQuestionRequest(
                QuestionType.FREE_TEXT,
                "What is 2+2?",
                5,
                0,
                null,
                "4",
                false
        );

        CreateOptionRequest o1 = new CreateOptionRequest("A", false);
        CreateOptionRequest o2 = new CreateOptionRequest("B", true);

        CreateQuestionRequest q2 = new CreateQuestionRequest(
                QuestionType.SINGLE_CHOICE,
                "Pick the correct",
                3,
                1,
                List.of(o1, o2),
                null,
                false
        );

        return new CreateQuizRequest("My Quiz", "Desc", List.of(q1, q2));
    }
}
