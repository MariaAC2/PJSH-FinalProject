package com.quizapp.dtos;

import java.util.List;

public record QuizResponse(
        Long id,
        String title,
        String description,
        List<QuestionResponse> questions
) {}

