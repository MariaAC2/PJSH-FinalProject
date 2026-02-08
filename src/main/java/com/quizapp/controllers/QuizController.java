package com.quizapp.controllers;

import com.quizapp.dtos.CreateQuizRequest;
import com.quizapp.dtos.QuizResponse;
import com.quizapp.services.QuizService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quizzes")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuizResponse createTest(@RequestBody CreateQuizRequest req) {
        return quizService.createQuiz(req);
    }

    @GetMapping("/{id}")
    public QuizResponse getTest(@PathVariable Long id) {
        return quizService.getQuiz(id);
    }

    @PutMapping("/{id}")
    public QuizResponse updateTest(@PathVariable Long id, @RequestBody CreateQuizRequest req) {
        return quizService.updateQuiz(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTest(@PathVariable Long id) {
        quizService.deleteQuiz(id);
    }
}

