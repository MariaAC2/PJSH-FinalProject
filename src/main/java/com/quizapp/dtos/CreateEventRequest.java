package com.quizapp.dtos;

import java.time.Instant;

public record CreateEventRequest(
        Long testId,
        String name,
        Integer durationSeconds,
        Instant joinClosesAt
) { }

