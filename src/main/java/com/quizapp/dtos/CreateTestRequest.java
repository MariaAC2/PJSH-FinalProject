package com.quizapp.dtos;

import java.util.List;

public record CreateTestRequest(
        String title,
        String description,
        List<CreateQuestionRequest> questions
) { }
