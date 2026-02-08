package com.quizapp.dtos;

import java.util.List;

public record CreateQuizRequest(
        String title,
        String description,
        List<CreateQuestionRequest> questions
) { }
