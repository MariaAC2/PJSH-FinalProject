package com.quizapp.dtos;

import com.quizapp.enums.QuestionType;

import java.util.List;

public record QuestionResponse(
        Long id,
        QuestionType type,
        String prompt,
        int points,
        int position,
        List<OptionResponse> options,
        String correctText,
        boolean caseSensitive
) { }
