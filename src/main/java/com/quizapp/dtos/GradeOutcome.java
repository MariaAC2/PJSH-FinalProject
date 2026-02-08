package com.quizapp.dtos;

import com.quizapp.entities.Answer;

public record GradeOutcome(
        Answer answerEntity,
        AnswerResult result,
        int awardedPoints
) {}
