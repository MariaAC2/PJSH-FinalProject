package com.quizapp.services;

import com.quizapp.dtos.AnswerResult;
import com.quizapp.dtos.AnswerSubmission;
import com.quizapp.dtos.AttemptResponse;
import com.quizapp.dtos.AttemptSubmissionRequest;
import com.quizapp.entities.*;
import com.quizapp.enums.AttemptStatus;
import com.quizapp.enums.EventStatus;
import com.quizapp.repositories.AttemptRepository;
import com.quizapp.repositories.EventParticipantRepository;
import com.quizapp.repositories.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttemptServiceTest {

    @Mock EventRepository eventRepository;
    @Mock EventParticipantRepository participantRepository;
    @Mock AttemptRepository attemptRepository;
    @Mock UserService userService;

    @InjectMocks AttemptService attemptService;

    @Captor ArgumentCaptor<Attempt> attemptCaptor;

    @Test
    void startAttempt_createsAttempt() {
        Quiz quiz = buildQuizWithQuestions(List.of(freeTextQuestion(1L, 2, "Paris", false)));
        Event event = buildRunningEventWithQuiz(10L, quiz);

        User user = new User();
        user.setId(5L);

        EventParticipant participant = new EventParticipant();
        participant.setEvent(event);
        participant.setUser(user);

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(user);
        when(participantRepository.findByEventAndUser(event, user)).thenReturn(Optional.of(participant));
        when(attemptRepository.existsByParticipant(participant)).thenReturn(false);

        when(attemptRepository.save(any(Attempt.class))).thenAnswer(invocation -> {
            Attempt attempt = invocation.getArgument(0);
            attempt.setId(99L);
            return attempt;
        });

        var response = attemptService.startAttempt(10L);

        assertEquals(99L, response.attemptId());
        assertEquals(AttemptStatus.IN_PROGRESS, response.status());
        assertEquals(event.getEndsAt(), response.endsAt());

        verify(attemptRepository).save(attemptCaptor.capture());
        Attempt saved = attemptCaptor.getValue();
        assertSame(event, saved.getEvent());
        assertSame(participant, saved.getParticipant());
        assertEquals(AttemptStatus.IN_PROGRESS, saved.getStatus());
        assertNotNull(saved.getStartedAt());
    }

    @Test
    void cancelAttempt_marksAttemptAbandoned() {
        Quiz quiz = buildQuizWithQuestions(List.of(freeTextQuestion(1L, 2, "Paris", false)));
        Event event = buildRunningEventWithQuiz(22L, quiz);

        User user = new User();
        user.setId(8L);

        EventParticipant participant = new EventParticipant();
        participant.setEvent(event);
        participant.setUser(user);

        Attempt attempt = new Attempt();
        attempt.setStatus(AttemptStatus.IN_PROGRESS);

        when(eventRepository.findById(22L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(user);
        when(participantRepository.findByEventAndUser(event, user)).thenReturn(Optional.of(participant));
        when(attemptRepository.findByParticipant(participant)).thenReturn(Optional.of(attempt));

        attemptService.cancelAttempt(22L);

        assertEquals(AttemptStatus.ABANDONED, attempt.getStatus());
        assertNotNull(attempt.getAbandonedAt());
    }

    @Test
    void submitAttempt_whenAnswersMissing_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attemptService.submitAttempt(1L, null));

        assertEquals("Answers are required", ex.getMessage());
        verifyNoInteractions(eventRepository, participantRepository, attemptRepository, userService);
    }

    @Test
    void submitAttempt_whenEventMissing_throws() {
        when(eventRepository.findById(44L)).thenReturn(Optional.empty());

        AttemptSubmissionRequest req = new AttemptSubmissionRequest(List.of(
                new AnswerSubmission(1L, null, "Paris")
        ));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attemptService.submitAttempt(44L, req));

        assertEquals("Event not found", ex.getMessage());
        verifyNoInteractions(participantRepository, attemptRepository, userService);
    }

    @Test
    void submitAttempt_whenParticipantMissing_throws() {
        Event event = new Event();
        event.setId(10L);

        event.setStatus(EventStatus.RUNNING);
        Instant now = Instant.now();
        event.setStartsAt(now.minusSeconds(60));
        event.setEndsAt(now.plusSeconds(600));

        event.setQuiz(buildQuizWithQuestions(
                List.of(freeTextQuestion(1L, 2, "Paris", false))
        ));

        User user = new User();
        user.setId(1L);

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(user);

        when(participantRepository.findByEventAndUser(event, user))
                .thenReturn(Optional.empty());

        AttemptSubmissionRequest req = new AttemptSubmissionRequest(List.of(
                new AnswerSubmission(1L, null, "Paris")
        ));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attemptService.submitAttempt(10L, req));

        assertEquals("Participant not found for this user and event", ex.getMessage());

        // It must fail before touching attempts
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void submitAttempt_whenQuizMissing_throws() {
        Event event = new Event();
        event.setId(12L);

        event.setStatus(EventStatus.RUNNING);
        Instant now = Instant.now();
        event.setStartsAt(now.minusSeconds(60));
        event.setEndsAt(now.plusSeconds(600));

        event.setQuiz(null);

        User user = new User();
        EventParticipant participant = new EventParticipant();
        participant.setEvent(event);
        participant.setUser(user);

        when(eventRepository.findById(12L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(user);
        when(participantRepository.findByEventAndUser(event, user)).thenReturn(Optional.of(participant));

        AttemptSubmissionRequest req = new AttemptSubmissionRequest(List.of(
                new AnswerSubmission(1L, null, "Paris")
        ));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> attemptService.submitAttempt(12L, req));

        assertEquals("Event has no quiz attached", ex.getMessage());

        verifyNoInteractions(attemptRepository);
    }

    @Test
    void submitAttempt_whenQuestionIdInvalid_throws() {
        Event event = new Event();
        event.setId(13L);

        event.setStatus(EventStatus.RUNNING);
        Instant now = Instant.now();
        event.setStartsAt(now.minusSeconds(60));
        event.setEndsAt(now.plusSeconds(600));

        Quiz quiz = buildQuizWithQuestions(List.of(
                freeTextQuestion(1L, 2, "Paris", false)
        ));
        event.setQuiz(quiz);

        User user = new User();
        user.setId(1L);

        EventParticipant participant = new EventParticipant();
        participant.setEvent(event);
        participant.setUser(user);

        when(eventRepository.findById(13L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(user);
        when(participantRepository.findByEventAndUser(event, user)).thenReturn(Optional.of(participant));

        Attempt existing = new Attempt();
        existing.setStatus(AttemptStatus.IN_PROGRESS);
        when(attemptRepository.findByParticipant(participant)).thenReturn(Optional.of(existing));

        AttemptSubmissionRequest req = new AttemptSubmissionRequest(
                List.of(new AnswerSubmission(99L, null, "Paris")) // invalid id
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attemptService.submitAttempt(13L, req));

        assertEquals("Invalid questionId: 99", ex.getMessage());

        // It should fail before saving
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void submitAttempt_whenDuplicateQuestionId_throws() {
        // quiz with question id=1
        Quiz quiz = buildQuizWithQuestions(List.of(
                freeTextQuestion(1L, 2, "Paris", false)
        ));

        Event event = new Event();
        event.setId(11L);
        event.setQuiz(quiz);
        event.setStatus(EventStatus.RUNNING);

        Instant now = Instant.now();
        event.setStartsAt(now.minusSeconds(60));
        event.setEndsAt(now.plusSeconds(600));

        User user = new User();
        user.setId(1L);

        EventParticipant participant = new EventParticipant();
        participant.setEvent(event);
        participant.setUser(user);

        when(eventRepository.findById(11L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(user);
        when(participantRepository.findByEventAndUser(event, user)).thenReturn(Optional.of(participant));

        Attempt existing = new Attempt();
        existing.setStatus(AttemptStatus.IN_PROGRESS);
        when(attemptRepository.findByParticipant(participant)).thenReturn(Optional.of(existing));

        AttemptSubmissionRequest req = new AttemptSubmissionRequest(List.of(
                new AnswerSubmission(1L, null, "Paris"),
                new AnswerSubmission(1L, null, "Paris") // duplicate
        ));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attemptService.submitAttempt(11L, req));

        assertEquals("Duplicate answer for questionId: 1", ex.getMessage());

        // It should fail before saving
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void submitAttempt_gradesQuestionsAndSavesAttempt() {
        FreeTextQuestion freeTextQuestion = freeTextQuestion(1L, 2, "Paris", false);

        Option scCorrect = option(10L, "Paris", true, 1);
        Option scWrong = option(11L, "London", false, 2);
        SingleChoiceQuestion singleChoiceQuestion = singleChoiceQuestion(2L, 3, scCorrect, scWrong);

        Option mcCorrect1 = option(20L, "A", true, 1);
        Option mcCorrect2 = option(21L, "B", true, 2);
        Option mcWrong = option(22L, "C", false, 3);
        MultipleChoiceQuestion multipleChoiceQuestion = multipleChoiceQuestion(3L, 4, mcCorrect1, mcCorrect2, mcWrong);

        Quiz quiz = buildQuizWithQuestions(List.of(freeTextQuestion, singleChoiceQuestion, multipleChoiceQuestion));

        Event event = new Event();
        event.setId(12L);
        event.setQuiz(quiz);
        event.setStatus(EventStatus.RUNNING);

        Instant now = Instant.now();
        event.setStartsAt(now.minusSeconds(60));  // already started
        event.setEndsAt(now.plusSeconds(600));    // ends in the future

        User user = new User();
        user.setId(999L); // optional but good hygiene

        EventParticipant participant = new EventParticipant();
        participant.setEvent(event);
        participant.setUser(user);

        when(eventRepository.findById(12L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(user);
        when(participantRepository.findByEventAndUser(event, user)).thenReturn(Optional.of(participant));

        Attempt existingAttempt = new Attempt();
        existingAttempt.setStatus(AttemptStatus.IN_PROGRESS);
        when(attemptRepository.findByParticipant(participant)).thenReturn(Optional.of(existingAttempt));

        // save() returns submitted attempt with id
        when(attemptRepository.save(any(Attempt.class))).thenAnswer(invocation -> {
            Attempt attempt = invocation.getArgument(0);
            attempt.setId(101L);
            return attempt;
        });

        AttemptSubmissionRequest req = new AttemptSubmissionRequest(List.of(
                new AnswerSubmission(1L, null, "paris"),
                new AnswerSubmission(2L, List.of(10L), null),
                new AnswerSubmission(3L, List.of(20L, 22L), null)
        ));

        AttemptResponse resp = attemptService.submitAttempt(12L, req);

        assertEquals(101L, resp.id());
        assertEquals(5, resp.score());
        assertEquals(9, resp.maxScore());
        assertEquals(AttemptStatus.SUBMITTED, resp.status());
        assertEquals(3, resp.answers().size());

        AnswerResult freeTextResult = resp.answers().get(0);
        assertEquals(1L, freeTextResult.questionId());
        assertTrue(freeTextResult.correct());
        assertEquals(2, freeTextResult.pointsAwarded());

        AnswerResult singleChoiceResult = resp.answers().get(1);
        assertEquals(2L, singleChoiceResult.questionId());
        assertTrue(singleChoiceResult.correct());
        assertEquals(3, singleChoiceResult.pointsAwarded());

        AnswerResult multipleChoiceResult = resp.answers().get(2);
        assertEquals(3L, multipleChoiceResult.questionId());
        assertFalse(multipleChoiceResult.correct());
        assertEquals(0, multipleChoiceResult.pointsAwarded());

        verify(attemptRepository).save(attemptCaptor.capture());
        Attempt savedAttempt = attemptCaptor.getValue();
        assertEquals(5, savedAttempt.getScore());
        assertEquals(9, savedAttempt.getMaxScore());
        assertEquals(AttemptStatus.SUBMITTED, savedAttempt.getStatus());
        assertEquals(3, savedAttempt.getAnswers().size());
        assertSame(event, savedAttempt.getEvent());
        assertSame(participant, savedAttempt.getParticipant());
        assertNotNull(savedAttempt.getStartedAt());
        assertNotNull(savedAttempt.getSubmittedAt());
    }

    private Event buildRunningEventWithQuiz(Long id, Quiz quiz) {
        Event event = new Event();
        event.setId(id);
        event.setQuiz(quiz);
        event.setStatus(EventStatus.RUNNING);

        Instant now = Instant.now();
        event.setStartsAt(now.minusSeconds(60));
        event.setEndsAt(now.plusSeconds(600));
        return event;
    }

    private Quiz buildQuizWithQuestions(List<Question> questions) {
        Quiz quiz = new Quiz();
        quiz.setQuestions(questions);
        for (Question question : questions) {
            question.setQuiz(quiz);
        }
        return quiz;
    }

    private FreeTextQuestion freeTextQuestion(Long id, int points, String correctAnswer, boolean caseSensitive) {
        FreeTextQuestion question = new FreeTextQuestion();
        question.setId(id);
        question.setPoints(points);
        question.setCorrectAnswer(correctAnswer);
        question.setCaseSensitive(caseSensitive);
        return question;
    }

    private SingleChoiceQuestion singleChoiceQuestion(Long id, int points, Option... options) {
        SingleChoiceQuestion question = new SingleChoiceQuestion();
        question.setId(id);
        question.setPoints(points);
        question.setOptions(List.of(options));
        for (Option option : options) {
            option.setQuestion(question);
        }
        return question;
    }

    private MultipleChoiceQuestion multipleChoiceQuestion(Long id, int points, Option... options) {
        MultipleChoiceQuestion question = new MultipleChoiceQuestion();
        question.setId(id);
        question.setPoints(points);
        question.setOptions(List.of(options));
        for (Option option : options) {
            option.setQuestion(question);
        }
        return question;
    }

    private Option option(Long id, String text, boolean correct, int position) {
        Option option = new Option();
        option.setId(id);
        option.setOptionText(text);
        option.setCorrect(correct);
        option.setPosition(position);
        return option;
    }
}
