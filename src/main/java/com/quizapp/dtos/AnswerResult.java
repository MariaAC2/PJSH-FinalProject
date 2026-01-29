package com.quizapp.dtos;

public record AnswerResult(Long questionId, Boolean correct, Integer pointsAwarded) {}

