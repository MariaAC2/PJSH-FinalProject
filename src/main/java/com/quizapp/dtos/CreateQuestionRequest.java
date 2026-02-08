package com.quizapp.dtos;

import com.quizapp.enums.QuestionType;

import java.util.List;

public record CreateQuestionRequest(
        QuestionType type,
        String prompt,
        int points,
        int position,
        List<CreateOptionRequest> options,
        String correctAnswer,
        boolean caseSensitive
) { }
