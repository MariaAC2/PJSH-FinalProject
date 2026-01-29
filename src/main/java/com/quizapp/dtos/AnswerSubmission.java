package com.quizapp.dtos;

import java.util.List;

public record AnswerSubmission(Long questionId, List<Long> selectedOptionIds, String textAnswer) {}

