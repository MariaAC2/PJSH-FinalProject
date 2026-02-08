package com.quizapp.services;

import com.quizapp.audit.Auditable;
import com.quizapp.dtos.*;
import com.quizapp.entities.*;
import com.quizapp.enums.AttemptStatus;
import com.quizapp.enums.EventStatus;
import com.quizapp.repositories.AttemptRepository;
import com.quizapp.repositories.EventParticipantRepository;
import com.quizapp.repositories.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttemptService {

    private final EventRepository eventRepository;
    private final EventParticipantRepository participantRepository;
    private final AttemptRepository attemptRepository;
    private final UserService userService;

    public AttemptService(EventRepository eventRepository,
                          EventParticipantRepository participantRepository,
                          AttemptRepository attemptRepository,
                          UserService userService) {
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
        this.attemptRepository = attemptRepository;
        this.userService = userService;
    }

    // =========================
    // Grading (unchanged)
    // =========================

    private GradeOutcome gradeQuestion(Question q, AnswerSubmission sub, Attempt attempt) {
        if (q instanceof FreeTextQuestion ftq) return gradeFreeText(ftq, sub, attempt);
        if (q instanceof SingleChoiceQuestion scq) return gradeSingleChoice(scq, sub, attempt);
        if (q instanceof MultipleChoiceQuestion mcq) return gradeMultipleChoice(mcq, sub, attempt);
        throw new IllegalStateException("Unknown question type: " + q.getClass().getSimpleName());
    }

    private GradeOutcome gradeFreeText(FreeTextQuestion q, AnswerSubmission sub, Attempt attempt) {
        int qPoints = q.getPoints();
        String answerText = (sub != null) ? sub.textAnswer() : null;

        boolean correct = false;
        if (answerText != null && q.getCorrectAnswer() != null) {
            String trimmed = answerText.trim();
            correct = q.isCaseSensitive()
                    ? q.getCorrectAnswer().equals(trimmed)
                    : q.getCorrectAnswer().equalsIgnoreCase(trimmed);
        }

        TextAnswer ta = new TextAnswer();
        ta.setAttempt(attempt);
        ta.setQuestion(q);
        ta.setAnswerText(answerText);
        ta.setCorrect(correct);
        ta.setPointsAwarded(correct ? qPoints : 0);

        int awarded = ta.getPointsAwarded();
        return new GradeOutcome(
                ta,
                new AnswerResult(q.getId(), correct, awarded),
                awarded
        );
    }

    private GradeOutcome gradeSingleChoice(SingleChoiceQuestion q, AnswerSubmission sub, Attempt attempt) {
        int qPoints = q.getPoints();

        Map<Long, Option> optMap = q.getOptions().stream()
                .collect(Collectors.toMap(Option::getId, o -> o));

        Set<Long> selected = (sub != null && sub.selectedOptionIds() != null)
                ? new HashSet<>(sub.selectedOptionIds())
                : Collections.emptySet();

        int awarded = 0;
        boolean correct = false;

        if (selected.size() == 1) {
            Long chosen = selected.iterator().next();
            Option opt = optMap.get(chosen);
            if (opt != null && opt.isCorrect()) {
                awarded = qPoints;
                correct = true;
            }
        }

        ChoiceAnswer ca = new ChoiceAnswer();
        ca.setAttempt(attempt);
        ca.setQuestion(q);
        ca.setCorrect(correct);
        ca.setPointsAwarded(awarded);

        for (Long selId : selected) {
            Option opt = optMap.get(selId);
            if (opt != null) {
                ChoiceAnswerSelection sel = new ChoiceAnswerSelection();
                sel.setAnswer(ca);
                sel.setOption(opt);
                ca.getSelections().add(sel);
            }
        }

        return new GradeOutcome(
                ca,
                new AnswerResult(q.getId(), correct, awarded),
                awarded
        );
    }

    private GradeOutcome gradeMultipleChoice(MultipleChoiceQuestion q, AnswerSubmission sub, Attempt attempt) {
        int qPoints = q.getPoints();

        Map<Long, Option> optMap = q.getOptions().stream()
                .collect(Collectors.toMap(Option::getId, o -> o));

        Set<Long> correctOptionIds = q.getOptions().stream()
                .filter(Option::isCorrect)
                .map(Option::getId)
                .collect(Collectors.toSet());

        Set<Long> selected = (sub != null && sub.selectedOptionIds() != null)
                ? new HashSet<>(sub.selectedOptionIds())
                : Collections.emptySet();

        int correctCount = correctOptionIds.size();
        int correctSelected = 0;
        int incorrectSelected = 0;

        for (Long sel : selected) {
            Option opt = optMap.get(sel);
            if (opt == null) continue;
            if (opt.isCorrect()) correctSelected++; else incorrectSelected++;
        }

        int raw = Math.max(0, correctSelected - incorrectSelected);
        int awarded = 0;
        if (correctCount > 0) {
            double frac = (double) raw / (double) correctCount;
            awarded = (int) Math.round(frac * qPoints);
        }

        ChoiceAnswer ca = new ChoiceAnswer();
        ca.setAttempt(attempt);
        ca.setQuestion(q);
        ca.setCorrect(awarded == qPoints);
        ca.setPointsAwarded(awarded);

        for (Long selId : selected) {
            Option opt = optMap.get(selId);
            if (opt != null) {
                ChoiceAnswerSelection sel = new ChoiceAnswerSelection();
                sel.setAnswer(ca);
                sel.setOption(opt);
                ca.getSelections().add(sel);
            }
        }

        return new GradeOutcome(
                ca,
                new AnswerResult(q.getId(), awarded == qPoints, awarded),
                awarded
        );
    }

    // =========================
    // Start Attempt
    // =========================
    @Transactional
    @Auditable(action = "start_attempt")
    public AttemptStartResponse startAttempt(Long eventId) {
        AttemptContext ctx = loadAttemptContextForStart(eventId);

        // 1 attempt total: if exists, block
        if (attemptRepository.existsByParticipant(ctx.participant())) {
            throw new IllegalStateException("Attempt already exists for this event");
        }

        Attempt attempt = new Attempt();
        attempt.setEvent(ctx.event());
        attempt.setParticipant(ctx.participant());
        attempt.setStartedAt(Instant.now());
        attempt.setStatus(AttemptStatus.IN_PROGRESS);

        Attempt saved = attemptRepository.save(attempt);

        return new AttemptStartResponse(saved.getId(), saved.getStatus(), ctx.event().getEndsAt());
    }

    // =========================
    // Submit Attempt
    // =========================

    @Transactional
    @Auditable(action = "submit_attempt")
    public AttemptResponse submitAttempt(Long eventId, AttemptSubmissionRequest req) {
        if (req == null || req.answers() == null) throw new IllegalArgumentException("Answers are required");
        if (req.answers().isEmpty()) throw new IllegalArgumentException("At least one answer is required");

        AttemptContext ctx = loadAttemptContextForSubmit(eventId); // includes status/time checks + 1-attempt rule

        Map<Long, Question> questionsById = ctx.quiz().getQuestions().stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        Map<Long, AnswerSubmission> byQ = submissionsByQuestionId(req, questionsById);

        Attempt attempt = newSubmittedAttempt(ctx.event(), ctx.participant());

        int totalMax = 0;
        int totalScore = 0;
        List<AnswerResult> results = new ArrayList<>();

        for (Question q : ctx.quiz().getQuestions()) {
            totalMax += q.getPoints();

            GradeOutcome outcome = gradeQuestion(q, byQ.get(q.getId()), attempt);

            attempt.getAnswers().add(outcome.answerEntity());
            results.add(outcome.result());
            totalScore += outcome.awardedPoints();
        }

        attempt.setMaxScore(totalMax);
        attempt.setScore(totalScore);

        Attempt saved = attemptRepository.save(attempt);
        return new AttemptResponse(saved.getId(), saved.getScore(), saved.getMaxScore(), saved.getStatus(), results);
    }

    @Transactional
    @Auditable(action = "cancel_attempt")
    public void cancelAttempt(Long eventId) {
        AttemptContext ctx = loadAttemptContextBase(eventId); // must be RUNNING etc.

        Attempt attempt = attemptRepository.findByParticipant(ctx.participant())
                .orElseThrow(() -> new IllegalStateException("Attempt not started"));

        if (attempt.getStatus() == AttemptStatus.SUBMITTED)
            throw new IllegalStateException("Attempt already submitted");

        if (attempt.getStatus() == AttemptStatus.ABANDONED)
            return;

        attempt.setStatus(AttemptStatus.ABANDONED);
        attempt.setAbandonedAt(Instant.now());
    }

    private static Map<Long, AnswerSubmission> submissionsByQuestionId(
            AttemptSubmissionRequest req,
            Map<Long, Question> questionsById
    ) {
        Map<Long, AnswerSubmission> byQ = new HashMap<>();

        for (AnswerSubmission a : req.answers()) {
            if (a == null || a.questionId() == null) continue;

            if (!questionsById.containsKey(a.questionId())) {
                throw new IllegalArgumentException("Invalid questionId: " + a.questionId());
            }
            if (byQ.putIfAbsent(a.questionId(), a) != null) {
                throw new IllegalArgumentException("Duplicate answer for questionId: " + a.questionId());
            }
        }

        return byQ;
    }

    // =========================
    // Context / lifecycle checks
    // =========================

    private AttemptContext loadAttemptContextBase(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        enforceEventIsAcceptingSubmissions(event);

        User currentUser = userService.getAuthenticatedUserEntity();

        EventParticipant participant = participantRepository
                .findByEventAndUser(event, currentUser)
                .orElseThrow(() -> new IllegalArgumentException("Participant not found for this user and event"));

        Quiz quiz = event.getQuiz();
        if (quiz == null) throw new IllegalStateException("Event has no quiz attached");

        return new AttemptContext(event, participant, quiz);
    }

    private AttemptContext loadAttemptContextForStart(Long eventId) {
        AttemptContext ctx = loadAttemptContextBase(eventId);

        if (attemptRepository.existsByParticipant(ctx.participant())) {
            throw new IllegalStateException("Attempt already exists for this event");
        }

        return ctx;
    }

    private AttemptContext loadAttemptContextForSubmit(Long eventId) {
        AttemptContext ctx = loadAttemptContextBase(eventId);

        Attempt attempt = attemptRepository.findByParticipant(ctx.participant())
                .orElseThrow(() -> new IllegalStateException("Attempt not started"));

        if (attempt.getStatus() == AttemptStatus.SUBMITTED) {
            throw new IllegalStateException("Attempt already submitted");
        }
        if (attempt.getStatus() == AttemptStatus.ABANDONED) {
            throw new IllegalStateException("Attempt was abandoned");
        }
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new IllegalStateException("Attempt is not in progress");
        }

        return ctx;
    }


    private void enforceEventIsAcceptingSubmissions(Event event) {
        // clearer message for cancellation
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new IllegalStateException("Event was cancelled");
        }

        // lazy auto-close by time (updates DB state within the current transaction)
        autoCloseIfExpired(event);

        // after auto-close, RUNNING is the only acceptable status
        if (event.getStatus() != EventStatus.RUNNING) {
            throw new IllegalStateException("Event is not running");
        }

        // sanity: RUNNING events should have timing initialized by startEvent()
        if (event.getStartsAt() == null || event.getEndsAt() == null) {
            throw new IllegalStateException("Event timing is not initialized");
        }

        Instant now = Instant.now();

        // if system clock or data got weird
        if (now.isBefore(event.getStartsAt())) {
            throw new IllegalStateException("Event has not started yet");
        }

        // defensive: in theory autoCloseIfExpired already handled this; keep for clarity
        if (!now.isBefore(event.getEndsAt())) {
            event.setStatus(EventStatus.CLOSED);
            throw new IllegalStateException("Event has ended");
        }
    }

    private void autoCloseIfExpired(Event event) {
        Instant endsAt = event.getEndsAt();
        if (event.getStatus() == EventStatus.RUNNING
                && endsAt != null
                && !Instant.now().isBefore(endsAt)) {
            event.setStatus(EventStatus.CLOSED);
        }
    }

    // =========================
    // Attempt creation
    // =========================

    private Attempt newSubmittedAttempt(Event event, EventParticipant participant) {
        Attempt attempt = new Attempt();
        attempt.setEvent(event);
        attempt.setParticipant(participant);

        Instant now = Instant.now();
        attempt.setStartedAt(now);
        attempt.setSubmittedAt(now);
        attempt.setStatus(AttemptStatus.SUBMITTED);

        return attempt;
    }

    // =========================
    // Helper record (keep it inside the service)
    // =========================

    private record AttemptContext(Event event, EventParticipant participant, Quiz quiz) {}
}
