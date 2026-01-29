package com.quizapp.controllers;

import com.quizapp.dtos.AttemptResponse;
import com.quizapp.dtos.AttemptSubmissionRequest;
import com.quizapp.services.AnswerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/{eventId}/attempts")
public class AttemptController {

    private final AnswerService answerService;

    public AttemptController(AnswerService answerService) {
        this.answerService = answerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AttemptResponse submitAttempt(@PathVariable Long eventId, @RequestBody AttemptSubmissionRequest req) {
        return answerService.submitAttempt(eventId, req);
    }
}

