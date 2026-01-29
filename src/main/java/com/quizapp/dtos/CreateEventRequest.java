package com.quizapp.dtos;

import java.time.Instant;

public record CreateEventRequest(
        Long testId,
        Instant startsAt,
        Instant endsAt,
        Integer durationSeconds,
        Integer capacity,
        String name
) { }

