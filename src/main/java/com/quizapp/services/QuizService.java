package com.quizapp.services;

import com.quizapp.audit.Auditable;
import com.quizapp.dtos.*;
import com.quizapp.entities.*;
import com.quizapp.enums.QuestionType;
import com.quizapp.enums.UserRole;
import com.quizapp.repositories.QuizRepository;
import com.quizapp.repositories.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuizService {

    private final QuizRepository quizRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public QuizService(QuizRepository quizRepository, UserRepository userRepository, UserService userService) {
        this.quizRepository = quizRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    /// Create: new test with questions (and options)
    @Transactional
    @PreAuthorize("isAuthenticated()")
    @Auditable(action = "create_test")
    public QuizResponse createQuiz(CreateQuizRequest req) {
        validateCreateQuizRequest(req);

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found in DB"));

        Quiz quiz = new Quiz();
        quiz.setTitle(req.title().trim());
        quiz.setDescription(req.description());

        quiz.setOwner(owner);

        // Build questions (and options) in-memory
        int position = 0;
        for (CreateQuestionRequest qReq : req.questions()) {
            Question q = mapQuestion(qReq, position);
            // link both sides
            q.setQuiz(quiz);
            quiz.getQuestions().add(q);
            position++;
        }

        // Save aggregate root; cascade should save children
        Quiz saved = quizRepository.save(quiz);

        return toResponse(saved);
    }

    /// Get a single test by ID
    @PreAuthorize("isAuthenticated()")
    public QuizResponse getQuiz(Long id) {
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));
        return toResponse(quiz);
    }

    /// Replace title/description/questions. Only owner or ADMIN allowed.
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public QuizResponse updateQuiz(Long id, CreateQuizRequest req) {
        validateCreateQuizRequest(req);

        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        User user = userService.getAuthenticatedUserEntity();
        if (!isOwnerOrAdmin(quiz, user)) {
            throw new AccessDeniedException("Only the quiz owner or an admin can update the quiz");
        }

        quiz.setTitle(req.title().trim());
        quiz.setDescription(req.description());

        // remove existing questions (orphanRemoval = true) and add new ones from request
        quiz.getQuestions().clear();
        int position = 0;
        for (CreateQuestionRequest qReq : req.questions()) {
            Question q = mapQuestion(qReq, position);
            q.setQuiz(quiz);
            quiz.getQuestions().add(q);
            position++;
        }

        Quiz saved = quizRepository.save(quiz);
        return toResponse(saved);
    }

    /// Delete test by ID. Only owner or ADMIN allowed.
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void deleteQuiz(Long id) {
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        User user = userService.getAuthenticatedUserEntity();
        if (isOwnerOrAdmin(quiz, user)) {
            throw new AccessDeniedException("Only the quiz owner or an admin can delete the quiz");
        }

        quizRepository.delete(quiz);
    }

    private boolean isOwnerOrAdmin(Quiz quiz, User user) {
        if (user == null) return false;
        if (user.getRole() == UserRole.ADMIN) return true;
        return quiz.getOwner() != null && quiz.getOwner().getId() != null && quiz.getOwner().getId().equals(user.getId());
    }

    /// Validation and mapping helpers
    private void validateCreateQuizRequest(CreateQuizRequest req) {
        if (req == null) throw new IllegalArgumentException("Request is required");
        if (req.title() == null || req.title().trim().isEmpty())
            throw new IllegalArgumentException("Title is required");

        if (req.questions() == null || req.questions().isEmpty())
            throw new IllegalArgumentException("A test must contain at least one question");

         if (req.questions().size() > 50)
             throw new IllegalArgumentException("Too many questions");
    }

    /// Validates question request and maps to entity. Position is required for ordering.
    private Question mapQuestion(CreateQuestionRequest qReq, int position) {
        if (qReq == null) throw new IllegalArgumentException("Question is required");
        if (qReq.type() == null) throw new IllegalArgumentException("Question type is required");
        if (qReq.prompt() == null || qReq.prompt().trim().isEmpty())
            throw new IllegalArgumentException("Question prompt is required");

        int points = (qReq.points() == 0) ? 1 : qReq.points();
        if (points <= 0) throw new IllegalArgumentException("Question points must be > 0");

        return switch (qReq.type()) {
            case FREE_TEXT -> mapFreeText(qReq, position, points);
            case SINGLE_CHOICE -> mapSingleChoice(qReq, position, points);
            case MULTIPLE_CHOICE -> mapMultipleChoice(qReq, position, points);
        };
    }

    /// Maps free-text question request to entity. Validates that correct answer is provided.
    private FreeTextQuestion mapFreeText(CreateQuestionRequest qReq, int position, int points) {
        FreeTextQuestion q = new FreeTextQuestion();
        q.setType(QuestionType.FREE_TEXT);
        q.setPrompt(qReq.prompt().trim());
        q.setPoints(points);
        q.setPosition(position);
        q.setCorrectAnswer(qReq.correctAnswer());

        return q;
    }

    /// Maps single-choice question request to entity. Validates that options are provided and exactly 1 is correct.
    private SingleChoiceQuestion mapSingleChoice(CreateQuestionRequest qReq, int position, int points) {
        validateOptions(qReq.options(), true);

        SingleChoiceQuestion q = new SingleChoiceQuestion();
        q.setType(QuestionType.SINGLE_CHOICE);
        q.setPrompt(qReq.prompt().trim());
        q.setPoints(points);
        q.setPosition(position);

        q.setOptions(buildOptions(q, qReq.options()));
        return q;
    }

    /// Maps multiple-choice question request to entity. Validates that options are provided and at least 1 is correct.
    private MultipleChoiceQuestion mapMultipleChoice(CreateQuestionRequest qReq, int position, int points) {
        validateOptions(qReq.options(), false);

        MultipleChoiceQuestion q = new MultipleChoiceQuestion();
        q.setType(QuestionType.MULTIPLE_CHOICE);
        q.setPrompt(qReq.prompt().trim());
        q.setPoints(points);
        q.setPosition(position);

        q.setOptions(buildOptions(q, qReq.options()));
        return q;
    }

    private void validateOptions(List<CreateOptionRequest> options, boolean singleChoice) {
        if (options == null || options.size() < 2) {
            throw new IllegalArgumentException("Choice questions must have at least 2 options");
        }

        int correctCount = 0;
        for (CreateOptionRequest opt : options) {
            if (opt == null) throw new IllegalArgumentException("Option is required");
            if (opt.text() == null || opt.text().trim().isEmpty())
                throw new IllegalArgumentException("Option text is required");
            if (opt.correct()) correctCount++;
        }

        if (singleChoice && correctCount != 1) {
            throw new IllegalArgumentException("Single choice must have exactly 1 correct option");
        }
        if (!singleChoice && correctCount < 1) {
            throw new IllegalArgumentException("Multiple choice must have at least 1 correct option");
        }
    }

    private List<Option> buildOptions(ChoiceQuestion question,
                                      List<CreateOptionRequest> options) {
        List<Option> result = new ArrayList<>();
        int pos = 0;

        for (CreateOptionRequest opt : options) {
            Option o = new Option();
            o.setQuestion(question);
            o.setOptionText(opt.text().trim());
            o.setCorrect(opt.correct());
            o.setPosition(pos);

            result.add(o);
            pos++;
        }

        return result;
    }

    private QuizResponse toResponse(Quiz quiz) {
        List<QuestionResponse> qs = new ArrayList<>();
        for (Question q : quiz.getQuestions()) {
            List<OptionResponse> opts = getOptionResponses(q);

            // For free-text questions we want to expose the correct answer and case sensitivity
            String correctText = null;
            boolean caseSensitive = false;
            if (q instanceof FreeTextQuestion ftq) {
                correctText = ftq.getCorrectAnswer();
                caseSensitive = ftq.isCaseSensitive();
            }

            qs.add(new QuestionResponse(
                    q.getId(),
                    q.getType(),
                    q.getPrompt(),
                    q.getPoints(),
                    q.getPosition(),
                    opts,
                    correctText,
                    caseSensitive
            ));
        }

        return new QuizResponse(quiz.getId(), quiz.getTitle(), quiz.getDescription(), qs);
    }

    private static List<OptionResponse> getOptionResponses(Question q) {
        List<OptionResponse> opts = List.of();

        // build option DTOs for choice questions
        if (q instanceof ChoiceQuestion cq) {
            List<OptionResponse> tmp = new ArrayList<>();
            for (Option o : cq.getOptions()) {
                // OptionResponse has (Long id, String text, boolean correct) — position is not part of the DTO
                tmp.add(new OptionResponse(o.getId(), o.getOptionText(), o.isCorrect()));
            }
            opts = tmp;
        }
        return opts;
    }
}
