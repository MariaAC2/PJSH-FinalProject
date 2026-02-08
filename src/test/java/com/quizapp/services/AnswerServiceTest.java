package com.quizapp.services;

import com.quizapp.dtos.AnswerResult;
import com.quizapp.dtos.AnswerSubmission;
import com.quizapp.dtos.AttemptResponse;
import com.quizapp.dtos.AttemptSubmissionRequest;
import com.quizapp.entities.*;
import com.quizapp.enums.AttemptStatus;
import com.quizapp.repositories.AttemptRepository;
import com.quizapp.repositories.EventParticipantRepository;
import com.quizapp.repositories.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnswerServiceTest {

    @Mock EventRepository eventRepository;
    @Mock EventParticipantRepository participantRepository;
    @Mock AttemptRepository attemptRepository;
    @Mock UserService userService;

    @InjectMocks AnswerService answerService;

    @Captor ArgumentCaptor<Attempt> attemptCaptor;

    @Test
    void submitAttempt_whenAnswersMissing_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> answerService.submitAttempt(1L, null));

        assertEquals("Answers are required", ex.getMessage());
        verifyNoInteractions(eventRepository, participantRepository, attemptRepository, userService);
    }

    @Test
    void submitAttempt_whenQuestionIdInvalid_throws() {
        Event event = new Event();
        event.setId(10L);
        Test test = buildTestWithQuestions(List.of(freeTextQuestion(1L, 2, "Paris", false)));
        event.setTest(test);

        User user = new User();
        EventParticipant participant = new EventParticipant();
        participant.setEvent(event);
        participant.setUser(user);

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(user);
        when(participantRepository.findByEventAndUser(event, user)).thenReturn(Optional.of(participant));

        AttemptSubmissionRequest req = new AttemptSubmissionRequest(
                List.of(new AnswerSubmission(99L, null, "Paris"))
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> answerService.submitAttempt(10L, req));

        assertEquals("Invalid questionId: 99", ex.getMessage());
        verifyNoInteractions(attemptRepository);
    }

    @Test
    void submitAttempt_whenDuplicateQuestionId_throws() {
        Event event = new Event();
        event.setId(11L);
        Test test = buildTestWithQuestions(List.of(freeTextQuestion(1L, 2, "Paris", false)));
        event.setTest(test);

        User user = new User();
        EventParticipant participant = new EventParticipant();
        participant.setEvent(event);
        participant.setUser(user);

        when(eventRepository.findById(11L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(user);
        when(participantRepository.findByEventAndUser(event, user)).thenReturn(Optional.of(participant));

        AttemptSubmissionRequest req = new AttemptSubmissionRequest(List.of(
                new AnswerSubmission(1L, null, "Paris"),
                new AnswerSubmission(1L, null, "Paris")
        ));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> answerService.submitAttempt(11L, req));

        assertEquals("Duplicate answer for questionId: 1", ex.getMessage());
        verifyNoInteractions(attemptRepository);
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

        Event event = new Event();
        event.setId(12L);
        Test test = buildTestWithQuestions(List.of(freeTextQuestion, singleChoiceQuestion, multipleChoiceQuestion));
        event.setTest(test);

        User user = new User();
        EventParticipant participant = new EventParticipant();
        participant.setEvent(event);
        participant.setUser(user);

        when(eventRepository.findById(12L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(user);
        when(participantRepository.findByEventAndUser(event, user)).thenReturn(Optional.of(participant));
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

        AttemptResponse resp = answerService.submitAttempt(12L, req);

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
    }

    private Test buildTestWithQuestions(List<Question> questions) {
        Test test = new Test();
        test.setQuestions(questions);
        for (Question question : questions) {
            question.setTest(test);
        }
        return test;
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
