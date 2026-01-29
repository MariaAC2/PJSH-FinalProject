package com.quizapp.dtos;

import com.quizapp.enums.AttemptStatus;

import java.util.List;

public record AttemptResponse(
        Long id,
        Integer score,
        Integer maxScore,
        AttemptStatus status,
        List<AnswerResult> answers
) {}
