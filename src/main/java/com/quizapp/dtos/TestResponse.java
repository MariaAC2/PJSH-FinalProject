package com.quizapp.dtos;

import java.util.List;

public record TestResponse(
        Long id,
        String title,
        String description,
        List<QuestionResponse> questions
) {}

