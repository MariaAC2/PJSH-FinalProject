package com.quizapp.controllers;

import com.quizapp.dtos.AttemptResponse;
import com.quizapp.dtos.AttemptStartResponse;
import com.quizapp.dtos.AttemptSubmissionRequest;
import com.quizapp.services.AttemptService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/{eventId}/attempts")
public class AttemptController {

    private final AttemptService attemptService;

    public AttemptController(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public AttemptStartResponse startAttempt(@PathVariable Long eventId) {
        return attemptService.startAttempt(eventId);
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.OK)
    public AttemptResponse submitAttempt(@PathVariable Long eventId, @RequestBody AttemptSubmissionRequest req) {
        return attemptService.submitAttempt(eventId, req);
    }

    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelAttempt(@PathVariable Long eventId) {
        attemptService.cancelAttempt(eventId);
    }
}

