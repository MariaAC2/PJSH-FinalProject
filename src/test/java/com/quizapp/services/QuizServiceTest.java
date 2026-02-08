package com.quizapp.services;

import com.quizapp.entities.Quiz;
import org.junit.jupiter.api.Test;
import com.quizapp.dtos.*;
import com.quizapp.entities.User;
import com.quizapp.enums.QuestionType;
import com.quizapp.enums.UserRole;
import com.quizapp.repositories.QuizRepository;
import com.quizapp.repositories.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock private QuizRepository quizRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserService userService;

    @InjectMocks private QuizService quizService;

    @BeforeEach
    void setUpSecurityContext() {
        // createQuiz() uses SecurityContextHolder.getContext().getAuthentication().getName()
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("owner@test.com", "N/A"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createQuiz_shouldPersistAggregate_andReturnResponse() {
        // arrange
        User owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@test.com");
        owner.setDisplayName("Owner");
        owner.setRole(UserRole.USER);

        when(userRepository.findByEmail("owner@test.com")).thenReturn(Optional.of(owner));

        CreateQuizRequest req = sampleCreateQuizRequest();

        // Return the saved object (with id) from repo
        when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> {
            Quiz t = inv.getArgument(0);
            t.setId(100L);
            // simulate ids created by JPA if you want (not mandatory for this Quiz)
            return t;
        });

        // act
        QuizResponse resp = quizService.createQuiz(req);

        // assert
        assertNotNull(resp);
        assertEquals(100L, resp.id());
        assertEquals("My Quiz", resp.title());
        assertNotNull(resp.questions());
        assertEquals(2, resp.questions().size());

        verify(quizRepository).save(any(Quiz.class));

        // also verify user lookup by email
        verify(userRepository).findByEmail("owner@test.com");
    }

    @Test
    void createQuiz_shouldThrow_whenNoQuestions() {
        CreateQuizRequest req = new CreateQuizRequest("T", "D", List.of());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> quizService.createQuiz(req));
        assertTrue(ex.getMessage().toLowerCase().contains("at least one question"));
    }

    @Test
    void getQuiz_shouldReturnResponse_whenFound() {
        // arrange
        Quiz quiz = new Quiz();
        quiz.setId(10L);
        quiz.setTitle("Title");
        quiz.setDescription("Desc");

        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));

        // act
        QuizResponse resp = quizService.getQuiz(10L);

        // assert
        assertEquals(10L, resp.id());
        assertEquals("Title", resp.title());
        verify(quizRepository).findById(10L);
    }

    @Test
    void getQuiz_shouldThrow_whenMissing() {
        when(quizRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> quizService.getQuiz(99L));
    }

    @Test
    void updateQuiz_shouldThrow_whenNotOwnerOrAdmin() {
        Quiz quiz = new Quiz();
        quiz.setId(1L);

        User owner = new User();
        owner.setId(10L);
        owner.setRole(UserRole.USER);
        quiz.setOwner(owner);

        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));

        User intruder = new User();
        intruder.setId(999L);
        intruder.setRole(UserRole.USER);
        when(userService.getAuthenticatedUserEntity()).thenReturn(intruder);

        CreateQuizRequest req = sampleCreateQuizRequest();

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> quizService.updateQuiz(1L, req));

        verify(quizRepository, never()).save(any());
    }

    // ------------------------
    // helpers
    // ------------------------

    private static CreateQuizRequest sampleCreateQuizRequest() {
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
