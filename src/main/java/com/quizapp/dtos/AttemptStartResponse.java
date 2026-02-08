package com.quizapp.dtos;

import com.quizapp.enums.AttemptStatus;

import java.time.Instant;

public record AttemptStartResponse(Long attemptId, AttemptStatus status, Instant endsAt) {}

