package com.quizapp.controllers;

import com.quizapp.dtos.AttemptResponse;
import com.quizapp.dtos.AttemptStartResponse;
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

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public AttemptStartResponse startAttempt(@PathVariable Long eventId) {
        return answerService.startAttempt(eventId);
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.OK)
    public AttemptResponse submitAttempt(@PathVariable Long eventId, @RequestBody AttemptSubmissionRequest req) {
        return answerService.submitAttempt(eventId, req);
    }

    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelAttempt(@PathVariable Long eventId) {
        answerService.cancelAttempt(eventId);
    }
}

