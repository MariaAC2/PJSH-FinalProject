package com.quizapp.dtos;

import java.util.List;

public record AttemptSubmissionRequest(
        List<AnswerSubmission> answers
) {}


