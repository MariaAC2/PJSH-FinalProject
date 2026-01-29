package com.quizapp.services;

import com.quizapp.audit.Auditable;
import com.quizapp.dtos.AttemptResponse;
import com.quizapp.dtos.AttemptSubmissionRequest;
import com.quizapp.dtos.AnswerSubmission;
import com.quizapp.dtos.AnswerResult;
import com.quizapp.entities.*;
import com.quizapp.enums.AttemptStatus;
import com.quizapp.repositories.AttemptRepository;
import com.quizapp.repositories.EventParticipantRepository;
import com.quizapp.repositories.EventRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnswerService {

    private final EventRepository eventRepository;
    private final EventParticipantRepository participantRepository;
    private final AttemptRepository attemptRepository;
    private final UserService userService;

    public AnswerService(EventRepository eventRepository,
                         EventParticipantRepository participantRepository,
                         AttemptRepository attemptRepository,
                         UserService userService) {
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
        this.attemptRepository = attemptRepository;
        this.userService = userService;
    }

    @Transactional
    @Auditable(action = "submit_attempt")
    public AttemptResponse submitAttempt(Long eventId, AttemptSubmissionRequest req) {
        if (req == null || req.answers() == null) throw new IllegalArgumentException("Answers are required");

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        // Authenticated user required
        User currentUser = userService.getAuthenticatedUserEntity();

        EventParticipant participant = participantRepository.findByEventAndUser(event, currentUser)
                .orElseThrow(() -> new IllegalArgumentException("Participant not found for this user and event"));

        Test test = event.getTest();
        if (test == null) throw new IllegalStateException("Event has no test attached");

        // map submitted answers by questionId for quick lookup
        Map<Long, AnswerSubmission> byQ = new HashMap<>();
        for (AnswerSubmission a : req.answers()) {
            if (a == null || a.questionId() == null) continue;
            byQ.put(a.questionId(), a);
        }

        Attempt attempt = new Attempt();
        attempt.setEvent(event);
        attempt.setParticipant(participant);
        attempt.setStartedAt(Instant.now());
        attempt.setSubmittedAt(Instant.now());
        attempt.setStatus(AttemptStatus.SUBMITTED);

        int totalMax = 0;
        int totalScore = 0;
        List<AnswerResult> results = new ArrayList<>();

        for (Question q : test.getQuestions()) {
            int qPoints = q.getPoints();
            totalMax += qPoints;
            AnswerSubmission sub = byQ.get(q.getId());

            if (q instanceof FreeTextQuestion ftq) {
                String answerText = (sub != null) ? sub.textAnswer() : null;
                boolean correct = false;
                if (answerText != null) {
                    String correctText = ftq.getCorrectAnswer();
                    if (correctText != null) {
                        if (ftq.isCaseSensitive()) {
                            correct = correctText.equals(answerText.trim());
                        } else {
                            correct = correctText.equalsIgnoreCase(answerText.trim());
                        }
                    }
                }

                TextAnswer ta = new TextAnswer();
                ta.setAttempt(attempt);
                ta.setQuestion(q);
                ta.setAnswerText(answerText);
                ta.setCorrect(correct);
                ta.setPointsAwarded(correct ? qPoints : 0);
                attempt.getAnswers().add(ta);

                totalScore += (correct ? qPoints : 0);
                results.add(new AnswerResult(q.getId(), correct, correct ? qPoints : 0));

            } else if (q instanceof ChoiceQuestion cq) {
                // build map of optionId -> Option & collect correct option ids
                Map<Long, Option> optMap = cq.getOptions().stream().collect(Collectors.toMap(Option::getId, o -> o));
                Set<Long> correctOptionIds = cq.getOptions().stream().filter(Option::isCorrect).map(Option::getId).collect(Collectors.toSet());

                Set<Long> selected = (sub != null && sub.selectedOptionIds() != null) ? new HashSet<>(sub.selectedOptionIds()) : Collections.emptySet();

                // Single choice: full points when selected correct option
                if (q instanceof SingleChoiceQuestion) {
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
                    attempt.getAnswers().add(ca);

                    // add selections
                    for (Long selId : selected) {
                        Option opt = optMap.get(selId);
                        if (opt != null) {
                            ChoiceAnswerSelection sel = new ChoiceAnswerSelection();
                            sel.setAnswer(ca);
                            sel.setOption(opt);
                            ca.getSelections().add(sel);
                        }
                    }

                    totalScore += awarded;
                    results.add(new AnswerResult(q.getId(), correct, awarded));

                } else if (q instanceof MultipleChoiceQuestion) {
                    // Partial credit policy: correctSelected - incorrectSelected, normalized by correctCount
                    int correctCount = correctOptionIds.size();
                    int correctSelected = 0;
                    int incorrectSelected = 0;
                    for (Long sel : selected) {
                        Option opt = optMap.get(sel);
                        if (opt == null) continue; // ignore invalid ids
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
                    attempt.getAnswers().add(ca);

                    for (Long selId : selected) {
                        Option opt = optMap.get(selId);
                        if (opt != null) {
                            ChoiceAnswerSelection sel = new ChoiceAnswerSelection();
                            sel.setAnswer(ca);
                            sel.setOption(opt);
                            ca.getSelections().add(sel);
                        }
                    }

                    totalScore += awarded;
                    results.add(new AnswerResult(q.getId(), awarded == qPoints, awarded));
                }
            }
        }

        attempt.setMaxScore(totalMax);
        attempt.setScore(totalScore);

        Attempt saved = attemptRepository.save(attempt);

        return new AttemptResponse(saved.getId(), saved.getScore(), saved.getMaxScore(), saved.getStatus(), results);
    }
}
